package com.sunnysuperman.sqlgenerator.eclipse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLGenerator {

	public enum MysqlType {

		BIGINT, INT, SMALLINT, TINYINT, BIT,

		DOUBLE, FLOAT, DECIMAL,

		VARCHAR, JSON
	}

	public static class TableColumn {
		private String name;
		private String javaName;
		private String comment;
		private String javaType;
		private boolean nullable;
		private int length;
		private int precision;
		private String[] columnDefinition;
		private boolean autoIncrement;
		private boolean primary;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getJavaName() {
			return javaName;
		}

		public void setJavaName(String javaName) {
			this.javaName = javaName;
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public String getJavaType() {
			return javaType;
		}

		public void setJavaType(String javaType) {
			this.javaType = javaType;
		}

		public boolean isNullable() {
			return nullable;
		}

		public void setNullable(boolean nullable) {
			this.nullable = nullable;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}

		public int getPrecision() {
			return precision;
		}

		public void setPrecision(int precision) {
			this.precision = precision;
		}

		public String[] getColumnDefinition() {
			return columnDefinition;
		}

		public void setColumnDefinition(String[] columnDefinition) {
			this.columnDefinition = columnDefinition;
		}

		public boolean isAutoIncrement() {
			return autoIncrement;
		}

		public void setAutoIncrement(boolean autoIncrement) {
			this.autoIncrement = autoIncrement;
		}

		public boolean isPrimary() {
			return primary;
		}

		public void setPrimary(boolean primary) {
			this.primary = primary;
		}

	}

	public static class TableDefinition {
		private String name;
		private String comment;
		private boolean mapCamelToUnderscore;
		private List<TableColumn> columns;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public boolean isMapCamelToUnderscore() {
			return mapCamelToUnderscore;
		}

		public void setMapCamelToUnderscore(boolean mapCamelToUnderscore) {
			this.mapCamelToUnderscore = mapCamelToUnderscore;
		}

		public List<TableColumn> getColumns() {
			return columns;
		}

		public void setColumns(List<TableColumn> columns) {
			this.columns = columns;
		}

	}

	private static final Map<String, MysqlType> typeMapping = new HashMap<>();

	static {
		// 基本类型及包装类型
		typeMapping.put("long", MysqlType.BIGINT);
		typeMapping.put(Long.class.getName(), MysqlType.BIGINT);

		typeMapping.put("int", MysqlType.INT);
		typeMapping.put(Integer.class.getName(), MysqlType.INT);

		typeMapping.put("short", MysqlType.SMALLINT);
		typeMapping.put(Short.class.getName(), MysqlType.SMALLINT);

		typeMapping.put("byte", MysqlType.TINYINT);
		typeMapping.put(Byte.class.getName(), MysqlType.TINYINT);

		typeMapping.put("boolean", MysqlType.BIT);
		typeMapping.put(Boolean.class.getName(), MysqlType.BIT);

		typeMapping.put("double", MysqlType.DOUBLE);
		typeMapping.put(Double.class.getName(), MysqlType.DOUBLE);

		typeMapping.put("float", MysqlType.FLOAT);
		typeMapping.put(Float.class.getName(), MysqlType.FLOAT);

		typeMapping.put("char", MysqlType.VARCHAR);
		typeMapping.put(Character.class.getName(), MysqlType.VARCHAR);

		// 字串&算术&日期
		typeMapping.put(String.class.getName(), MysqlType.VARCHAR);
		typeMapping.put(BigDecimal.class.getName(), MysqlType.DECIMAL);
		Class<?>[] dateTypes = new Class<?>[] { Date.class, LocalDateTime.class, LocalDate.class };
		for (Class<?> type : dateTypes) {
			typeMapping.put(type.getName(), MysqlType.BIGINT);
		}

		// 枚举
		typeMapping.put(Enumeration.class.getName(), MysqlType.TINYINT);
	}

	public static String generate(TableDefinition def) {
		StringBuilder sql = new StringBuilder();
		String tableName = def.name;
		sql.append("CREATE TABLE `").append(tableName).append("` (\n");

		TableColumn idColumn = def.columns.stream().filter(i -> i.primary).findAny().orElse(null);
		List<TableColumn> columns = def.columns;
		if (idColumn != null && def.columns.get(0) != idColumn) {
			columns = new ArrayList<>(def.columns);
			columns.remove(idColumn);
			columns.add(0, idColumn);
		}

		char blank = ' ';
		String indent = "  ";
		String newLine = ",\n";
		for (TableColumn column : columns) {
			String[] columnDefinition = column.columnDefinition;
			if (columnDefinition != null && columnDefinition.length > 0) {
				for (String line : columnDefinition) {
					line = line.trim();
					line = StringUtil.replaceAll(line, ",", StringUtil.EMPTY);
					line = StringUtil.replaceAll(line, "\"", "'");
					sql.append(indent).append(line).append(newLine);
				}
			} else {
				sql.append(indent);
				String columnName = columnName(column, def);
				MysqlType sqlType = ensureSqlTypeFromJavaType(column.javaType);
				// `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
				// `name` VARCHAR(255) NOT NULL COMMENT '',
				sql.append('`').append(columnName).append('`');
				sql.append(blank).append(sqlType);
				if (sqlType == MysqlType.VARCHAR) {
					sql.append('(').append(column.length).append(')');
				}
				int floatLength = getFloatLength(sqlType);
				if (floatLength > 0) {
					sql.append('(').append(floatLength).append(",").append(column.precision).append(')');
				}
				sql.append(blank).append(column.nullable ? "DEFAULT" : "NOT").append(" NULL");
				if (column.autoIncrement) {
					sql.append(blank).append("AUTO_INCREMENT");
				}
				sql.append(blank).append("COMMENT");
				sql.append(blank).append("'").append(StringUtil.or(column.comment, StringUtil.EMPTY)).append("'");
				sql.append(newLine);
			}
		}

		if (idColumn != null) {
			sql.append(blank).append(blank).append("PRIMARY KEY (`").append(columnName(idColumn, def)).append("`)\n");
		}

		// 移除最后一个逗号
		if (sql.toString().endsWith(",\n")) {
			sql = new StringBuilder(sql.substring(0, sql.length() - 2) + "\n");
		}

		sql.append(") ENGINE = InnoDB");
		if (idColumn != null && idColumn.autoIncrement) {
			sql.append(" AUTO_INCREMENT=1");
		}
		sql.append(" DEFAULT CHARSET = utf8mb4 COMMENT = '").append(StringUtil.or(def.comment, StringUtil.EMPTY))
				.append("';");
		return sql.toString();
	}

	private static String columnName(TableColumn column, TableDefinition def) {
		if (StringUtil.isNotEmpty(column.name)) {
			return column.name;
		}
		return def.mapCamelToUnderscore ? StringUtil.camel2underscore(column.javaName) : column.javaName;
	}

	private static MysqlType ensureSqlTypeFromJavaType(String javaType) {
		MysqlType sqlType = typeMapping.get(javaType);
		return sqlType != null ? sqlType : MysqlType.JSON;
	}

	private static int getFloatLength(MysqlType sqlType) {
		if (sqlType == MysqlType.FLOAT) {
			return 10;
		}
		if (sqlType == MysqlType.DOUBLE || sqlType == MysqlType.DECIMAL) {
			return 20;
		}
		return -1;
	}

}
