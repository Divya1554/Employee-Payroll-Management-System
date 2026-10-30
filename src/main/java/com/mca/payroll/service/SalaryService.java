package com.mca.payroll.service;

import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.SalaryStructureDao;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.dto.SalaryRequest;
import com.mca.payroll.dto.SalaryStructureDto;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.Role;
import com.mca.payroll.entity.SalaryComponents;
import com.mca.payroll.entity.SalaryStructure;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.exception.ResourceNotFoundException;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Salary structures with history. A revision never overwrites an old row: the previous structure
 * is closed (effectiveTo = new effectiveFrom - 1 day) and a new row is inserted. ADMIN and HR manage
 * structures; an EMPLOYEE may read only their own.
 */
public class SalaryService {

    private static final Logger LOG = LogManager.getLogger(SalaryService.class);

    private final SalaryStructureDao salaryDao;
    private final EmployeeDao employeeDao;

    public SalaryService() {
        this(new SalaryStructureDao(), new EmployeeDao());
    }

    public SalaryService(SalaryStructureDao salaryDao, EmployeeDao employeeDao) {
        this.salaryDao = salaryDao;
        this.employeeDao = employeeDao;
    }

    /** Validates every component (non-negative) and the relationship between them. */
    public static SalaryComponents validateComponents(SalaryRequest r) {
        BigDecimal basic = InputValidator.requirePositive(r.basicSalary(), "Basic salary");
        BigDecimal hra = InputValidator.requireNonNegative(r.houseRentAllowance(), "House rent allowance");
        BigDecimal medical = InputValidator.requireNonNegative(r.medicalAllowance(), "Medical allowance");
        BigDecimal conveyance = InputValidator.requireNonNegative(r.conveyanceAllowance(), "Conveyance allowance");
        BigDecimal other = InputValidator.requireNonNegative(r.otherAllowances(), "Other allowances");
        BigDecimal pf = InputValidator.requireNonNegative(r.providentFundDeduction(), "Provident fund deduction");
        BigDecimal pt = InputValidator.requireNonNegative(r.professionalTax(), "Professional tax");
        for (BigDecimal amount : List.of(basic, hra, medical, conveyance, other, pf, pt)) {
            if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
                throw new ValidationException("Amounts may have at most 2 decimal places.");
            }
            if (amount.compareTo(new BigDecimal("1000000000")) >= 0) {
                throw new ValidationException("Amount is too large.");
            }
        }
        SalaryComponents components = new SalaryComponents(basic.setScale(2), hra.setScale(2), medical.setScale(2),
                conveyance.setScale(2), other.setScale(2), pf.setScale(2), pt.setScale(2));
        if (components.fixedDeductions().compareTo(components.grossSalary()) > 0) {
            throw new ValidationException("Provident fund plus professional tax cannot exceed the gross salary.");
        }
        return components;
    }

    /**
     * Creates a salary structure for the employee and makes it the current one from effectiveFrom
     * (this is how a salary revision is made). The effective date must be after the previous
     * structure's start date.
     */
    public SalaryStructureDto createStructure(Long employeeId, SalaryRequest request) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        SalaryComponents components = validateComponents(request);
        if (request.effectiveFrom() == null) {
            throw new ValidationException("Effective-from date is required.");
        }
        return HibernateUtil.inTransaction(session ->
                DtoMapper.toDto(insertRevision(session, employeeId, components, request.effectiveFrom())));
    }

    /** Assigns a copy of an existing structure's amounts to another (or the same) employee. */
    public SalaryStructureDto assignStructure(Long sourceStructureId, Long employeeId, LocalDate effectiveFrom) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        if (effectiveFrom == null) {
            throw new ValidationException("Effective-from date is required.");
        }
        return HibernateUtil.inTransaction(session -> {
            SalaryStructure source = salaryDao.findById(session, sourceStructureId)
                    .orElseThrow(() -> new ResourceNotFoundException("Salary structure", sourceStructureId));
            return DtoMapper.toDto(insertRevision(session, employeeId, source.getComponents().copy(), effectiveFrom));
        });
    }

    /** Corrects the amounts of an existing structure (for example a data-entry error). Dates are unchanged. */
    public SalaryStructureDto updateStructure(Long structureId, SalaryRequest request) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        SalaryComponents components = validateComponents(request);
        return HibernateUtil.inTransaction(session -> {
            SalaryStructure structure = salaryDao.findById(session, structureId)
                    .orElseThrow(() -> new ResourceNotFoundException("Salary structure", structureId));
            structure.setComponents(components);
            session.flush();
            LOG.info("Salary structure corrected: {}", structure);
            return DtoMapper.toDto(structure);
        });
    }

    public List<SalaryStructureDto> getAllStructures() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session ->
                salaryDao.findAllWithEmployee(session).stream().map(DtoMapper::toDto).toList());
    }

    /** All revisions of one employee, newest first. */
    public List<SalaryStructureDto> getStructuresByEmployee(Long employeeId) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            if (employeeDao.findById(session, employeeId).isEmpty()) {
                throw new EmployeeNotFoundException(employeeId);
            }
            return salaryDao.findByEmployee(session, employeeId).stream().map(DtoMapper::toDto).toList();
        });
    }

    public SalaryStructureDto getCurrentStructure(Long employeeId) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            if (employeeDao.findById(session, employeeId).isEmpty()) {
                throw new EmployeeNotFoundException(employeeId);
            }
            return salaryDao.findEffective(session, employeeId, LocalDate.now())
                    .or(() -> salaryDao.findLatest(session, employeeId))
                    .map(DtoMapper::toDto)
                    .orElseThrow(() -> new ResourceNotFoundException("Salary structure of employee", employeeId));
        });
    }

    private SalaryStructure insertRevision(Session session, Long employeeId, SalaryComponents components,
                                           LocalDate effectiveFrom) {
        Employee employee = employeeDao.findById(session, employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        if (!employee.isActive()) {
            throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode() + " is inactive.");
        }
        if (effectiveFrom.isBefore(employee.getDateOfJoining())) {
            throw new ValidationException("The salary cannot take effect before the joining date ("
                    + employee.getDateOfJoining() + ").");
        }
        SalaryStructure previous = salaryDao.findLatest(session, employeeId).orElse(null);
        if (previous != null) {
            if (!effectiveFrom.isAfter(previous.getEffectiveFrom())) {
                throw new ValidationException("A revision must take effect after the current structure's start date ("
                        + previous.getEffectiveFrom() + "). To fix amounts, use 'Update salary structure'.");
            }
            LocalDate close = effectiveFrom.minusDays(1);
            if (previous.getEffectiveTo() == null || previous.getEffectiveTo().isAfter(close)) {
                previous.setEffectiveTo(close);
            }
        }
        SalaryStructure structure = new SalaryStructure(employee, components, effectiveFrom,
                SecurityContext.currentUsername());
        salaryDao.save(session, structure);
        session.flush();
        LOG.info("Salary structure created for {} effective {}", employee.getEmployeeCode(), effectiveFrom);
        return structure;
    }
}
