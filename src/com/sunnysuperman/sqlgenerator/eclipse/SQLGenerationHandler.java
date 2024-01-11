package com.sunnysuperman.sqlgenerator.eclipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.handlers.HandlerUtil;

import com.sunnysuperman.sqlgenerator.eclipse.SQLGenerator.TableColumn;
import com.sunnysuperman.sqlgenerator.eclipse.SQLGenerator.TableDefinition;

public class SQLGenerationHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().getSelection();
		boolean processed = false;
		if (selection instanceof IStructuredSelection) {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			if (element instanceof IType) {
				showSQL((IType) element);
				processed = true;
			} else if (element instanceof ICompilationUnit) {
				ICompilationUnit cu = (ICompilationUnit) element;
				try {
					showSQL(cu.getAllTypes()[0]);
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
				processed = true;
			}
		}
		if (!processed) {
			alert("请选择实体类");
		}
		return null;
	}

	private void alert(String msg) {
		MessageBox messageBox = new MessageBox(Display.getDefault().getActiveShell(), SWT.OK);
		messageBox.setMessage(msg);
		messageBox.open();
	}

	private void showSQL(IType type) {
		// 生成SQL
		String sql;
		try {
			sql = generateSQLByJavaClass(type);
		} catch (SQLGenerationException ex) {
			alert(ex.getMessage());
			return;
		} catch (Exception ex) {
			alert("生成SQL失败，请确保项目编译通过，并且是效的实体类");
			ex.printStackTrace();
			return;
		}
		// 显示SQL
		try {
			doShowSQL(sql);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void doShowSQL(String sql) {
		// 在UI线程中执行
		Display.getDefault().syncExec(() -> {
			// 创建对话框
			Shell activeShell = Display.getDefault().getActiveShell();
			Shell dialogShell = new Shell(activeShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
			dialogShell.setText("SQL Generator");
			dialogShell.setLayout(new GridLayout(1, false));

			// 创建文本区域以显示SQL
			Text sqlText = new Text(dialogShell, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
			sqlText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			sqlText.setText(sql);

			// 创建“拷贝SQL”按钮
			Button copyButton = new Button(dialogShell, SWT.PUSH);
			copyButton.setText("拷贝SQL");
			copyButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Clipboard clipboard = new Clipboard(Display.getDefault());
					TextTransfer textTransfer = TextTransfer.getInstance();
					clipboard.setContents(new Object[] { sqlText.getText() }, new Transfer[] { textTransfer });
					clipboard.dispose();

					// 可选：显示消息确认已复制
					MessageBox messageBox = new MessageBox(dialogShell, SWT.OK);
					messageBox.setMessage("SQL已复制到剪贴板。");
					messageBox.open();

					// 关闭对话框
					dialogShell.close();
				}
			});

			dialogShell.setSize(400, 300);
			dialogShell.open();
		});
	}

	private String generateSQLByJavaClass(IType type) throws SQLGenerationException, JavaModelException {
		IAnnotation entityAnnotation = getAnnotation(type, "Entity");
		if (entityAnnotation == null) {
			throw new SQLGenerationException("类未标记@Entity");
		}
		IAnnotation tableAnnotation = getAnnotation(type, "Table");
		if (tableAnnotation == null) {
			throw new SQLGenerationException("类未标记@Table");
		}
		// 表定义
		TableDefinition def = new TableDefinition();
		def.setName(AnnotationUtils.getStringValue(tableAnnotation, "name"));
		def.setComment(AnnotationUtils.getStringValue(tableAnnotation, "comment"));
		def.setMapCamelToUnderscore(AnnotationUtils.getBooleanValue(tableAnnotation, "mapCamelToUnderscore", true));
		def.setColumns(new ArrayList<>());
		// 遍历父类的字段
		ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
		IType[] superTypes = hierarchy.getAllSuperclasses(type);
		List<IType> superTypeList = Arrays.asList(superTypes);
		Collections.reverse(superTypeList);
		for (IType superType : superTypeList) {
			iterateFields(superType, def);
		}
		// 遍历本类的字段
		iterateFields(type, def);
		// 最终生成SQL
		return SQLGenerator.generate(def);
	}

	private void iterateFields(IType type, TableDefinition def) throws SQLGenerationException, JavaModelException {
		IField[] fields = type.getFields();
		for (IField field : fields) {
			IAnnotation columnAnnotation = getAnnotation(field, "Column");
			if (columnAnnotation == null) {
				continue;
			}
			TableColumn column = new TableColumn();
			def.getColumns().add(column);

			column.setName(AnnotationUtils.getStringValue(columnAnnotation, "name"));
			column.setComment(AnnotationUtils.getStringValue(columnAnnotation, "comment"));
			column.setJavaName(field.getElementName());
			column.setJavaType(getFieldJavaType(field, type));
			column.setColumnDefinition(AnnotationUtils.getStringArrayValue(columnAnnotation, "columnDefinition"));
			column.setNullable(AnnotationUtils.getBooleanValue(columnAnnotation, "nullable", true));
			column.setLength(AnnotationUtils.getIntValue(columnAnnotation, "length", 255));
			column.setPrecision(AnnotationUtils.getIntValue(columnAnnotation, "precision", 2));
			IAnnotation idAnnotation = getAnnotation(field, "Id");
			if (idAnnotation != null) {
				column.setNullable(false);
				column.setPrimary(true);
				column.setAutoIncrement(Objects.equals("IdStrategy.INCREMENT",
						AnnotationUtils.getStringValue(idAnnotation, "strategy")));
			}
			IAnnotation versionAnnotation = getAnnotation(field, "Version");
			if (versionAnnotation != null) {
				column.setNullable(false);
			}
		}
	}

	private String getFieldJavaType(IField field, IType type) throws SQLGenerationException, JavaModelException {
		String fieldTypeSignature = field.getTypeSignature();
		String fullyQualifiedTypeName = getFullyQualifiedName(fieldTypeSignature, type);
		IType fieldType = field.getDeclaringType().getJavaProject().findType(fullyQualifiedTypeName);
		if (fieldType == null) {
			throw new SQLGenerationException("请确保类编译通过");
		}
		// 枚举类统一转成Enumeration
		if (fieldType.isEnum()) {
			return Enumeration.class.getName();
		}
		if (getAnnotation(field, "ManyToOne") != null || getAnnotation(field, "OneToOne") != null) {
			IField relatedIdField = findIdField(fieldType);
			if (relatedIdField != null) {
				return getFieldJavaType(relatedIdField, fieldType);
			}
		}
		return fullyQualifiedTypeName;
	}

	private IField findIdField(IType type) throws JavaModelException {
		return Stream.of(type.getFields()).filter(field -> getAnnotation(field, "Id") != null).findAny().orElse(null);
	}

	private static String getFullyQualifiedName(String typeSignature, IType type) throws JavaModelException {
		String[][] resolvedTypeNames = type.resolveType(Signature.toString(typeSignature));
		if (resolvedTypeNames != null && resolvedTypeNames.length > 0) {
			// 取第一个解析结果，它通常是正确的
			String[] typeNameParts = resolvedTypeNames[0];
			return typeNameParts[0] + "." + typeNameParts[1]; // 包名.类名
		} else {
			return Signature.toString(typeSignature); // 如果无法解析，则返回原始类型签名
		}
	}

	private static IAnnotation getAnnotation(IAnnotatable annotatable, String name) {
		IAnnotation annotation = annotatable.getAnnotation(name);
		if (annotation == null || !annotation.exists()) {
			return null;
		}
		return annotation;
	}

}
