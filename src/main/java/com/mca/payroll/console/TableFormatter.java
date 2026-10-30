package com.mca.payroll.console;

import java.util.ArrayList;
import java.util.List;

/** Draws simple aligned text tables. */
public final class TableFormatter {

    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public TableFormatter(String... headers) {
        this.headers.addAll(List.of(headers));
    }

    public TableFormatter row(Object... cells) {
        List<String> row = new ArrayList<>();
        for (Object cell : cells) {
            row.add(cell == null ? "-" : String.valueOf(cell));
        }
        rows.add(row);
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public String render() {
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = headers.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
            widths[i] = Math.min(widths[i], 40);
        }
        StringBuilder sb = new StringBuilder();
        line(sb, widths);
        cells(sb, headers, widths);
        line(sb, widths);
        for (List<String> row : rows) {
            cells(sb, row, widths);
        }
        line(sb, widths);
        return sb.toString();
    }

    private static void line(StringBuilder sb, int[] widths) {
        sb.append('+');
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append('+');
        }
        sb.append('\n');
    }

    private static void cells(StringBuilder sb, List<String> cells, int[] widths) {
        sb.append('|');
        for (int i = 0; i < widths.length; i++) {
            String text = cells.get(i);
            if (text.length() > widths[i]) {
                text = text.substring(0, widths[i] - 1) + ".";
            }
            sb.append(' ').append(String.format("%-" + widths[i] + "s", text)).append(" |");
        }
        sb.append('\n');
    }
}
