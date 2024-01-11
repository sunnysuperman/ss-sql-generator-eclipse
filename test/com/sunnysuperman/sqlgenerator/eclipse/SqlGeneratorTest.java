package com.sunnysuperman.sqlgenerator.eclipse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sunnysuperman.sqlgenerator.eclipse.SQLGenerator.TableColumn;
import com.sunnysuperman.sqlgenerator.eclipse.SQLGenerator.TableDefinition;

class SqlGeneratorTest {

	@Test
	void test() {
		TableDefinition def = new TableDefinition();
		def.setName("sku");
		def.setComment("商品");

		TableColumn column1 = new TableColumn();
		column1.setName("id");
		column1.setPrimary(true);
		column1.setAutoIncrement(true);
		column1.setJavaType("long");

		TableColumn column2 = new TableColumn();
		column2.setName("price");
		column2.setComment("价格");
		column2.setJavaType(BigDecimal.class.getName());
		column2.setPrecision(2);

		TableColumn column3 = new TableColumn();
		column3.setName("title");
		column3.setComment("标题");
		column3.setJavaType(String.class.getName());
		column3.setLength(200);
		column3.setNullable(true);

		def.setColumns(List.of(column2, column1, column3));

		String sql = SQLGenerator.generate(def);
		System.out.println(sql);
		assertTrue(sql.contains("AUTO_INCREMENT"));
	}

	@Test
	void test2() {
		TableDefinition def = new TableDefinition();
		def.setName("sku");

		TableColumn column1 = new TableColumn();
		column1.setName("id");
		column1.setPrimary(false);
		column1.setJavaType("int");

		TableColumn column2 = new TableColumn();
		column2.setName("price");
		column2.setComment("价格");
		column2.setJavaType(BigDecimal.class.getName());

		TableColumn column3 = new TableColumn();
		column3.setName("title");
		column3.setComment("标题");
		column3.setJavaType(String.class.getName());
		column3.setLength(200);

		def.setColumns(List.of(column2, column1));

		String sql = SQLGenerator.generate(def);
		System.out.println(sql);
		assertTrue(!sql.contains("AUTO_INCREMENT"));
	}

}
