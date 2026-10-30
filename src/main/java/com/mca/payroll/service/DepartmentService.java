package com.mca.payroll.service;

import com.mca.payroll.dao.DepartmentDao;
import com.mca.payroll.dto.DepartmentDto;
import com.mca.payroll.dto.DepartmentRequest;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.entity.Department;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.DepartmentNotFoundException;
import com.mca.payroll.exception.DuplicateRecordException;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/** Business rules for departments. Only ADMIN and HR may use this service. */
public class DepartmentService {

    private static final Logger LOG = LogManager.getLogger(DepartmentService.class);

    private final DepartmentDao departmentDao;

    public DepartmentService() {
        this(new DepartmentDao());
    }

    public DepartmentService(DepartmentDao departmentDao) {
        this.departmentDao = departmentDao;
    }

    public DepartmentDto addDepartment(DepartmentRequest request) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String name = InputValidator.requiredText(request.name(), 100, "Department name");
        String description = InputValidator.optionalText(request.description(), 255, "Description");
        String location = InputValidator.optionalText(request.location(), 100, "Location");

        return HibernateUtil.inTransaction(session -> {
            if (departmentDao.findByName(session, name).isPresent()) {
                throw new DuplicateRecordException("A department named '" + name + "' already exists.");
            }
            Department department = new Department(name, description, location);
            departmentDao.save(session, department);
            session.flush();
            LOG.info("Department created: {}", department);
            return DtoMapper.toDto(department);
        });
    }

    public List<DepartmentDto> getAllDepartments() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session ->
                departmentDao.findAllOrdered(session).stream().map(DtoMapper::toDto).toList());
    }

    public DepartmentDto getDepartmentById(Long id) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> DtoMapper.toDto(load(session, id)));
    }

    public List<DepartmentDto> searchByName(String fragment) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String text = InputValidator.requireNotBlank(fragment, "Search text");
        return HibernateUtil.readOnly(session ->
                departmentDao.searchByName(session, text).stream().map(DtoMapper::toDto).toList());
    }

    public DepartmentDto updateDepartment(Long id, DepartmentRequest request) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String name = InputValidator.requiredText(request.name(), 100, "Department name");
        String description = InputValidator.optionalText(request.description(), 255, "Description");
        String location = InputValidator.optionalText(request.location(), 100, "Location");

        return HibernateUtil.inTransaction(session -> {
            Department department = load(session, id);
            departmentDao.findByName(session, name).ifPresent(other -> {
                if (!other.getDepartmentId().equals(id)) {
                    throw new DuplicateRecordException("A department named '" + name + "' already exists.");
                }
            });
            department.setDepartmentName(name);
            department.setDescription(description);
            department.setLocation(location);
            session.flush();
            LOG.info("Department updated: {}", department);
            return DtoMapper.toDto(department);
        });
    }

    /** Deletes a department only when no employee (active or inactive) belongs to it. */
    public void deleteDepartment(Long id) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        HibernateUtil.inTransaction(session -> {
            Department department = load(session, id);
            long employees = departmentDao.countEmployees(session, id);
            if (employees > 0) {
                throw new BusinessRuleViolationException("Department '" + department.getDepartmentName()
                        + "' still has " + employees + " employee(s). Move or remove them first.");
            }
            departmentDao.delete(session, department);
            LOG.info("Department deleted: {}", department);
            return null;
        });
    }

    private Department load(org.hibernate.Session session, Long id) {
        return departmentDao.findById(session, id).orElseThrow(() -> new DepartmentNotFoundException(id));
    }
}
