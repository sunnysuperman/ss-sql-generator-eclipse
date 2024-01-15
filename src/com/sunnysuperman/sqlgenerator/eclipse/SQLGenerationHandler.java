package com.sunnysuperman.sqlgenerator.eclipse;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
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
		Shell shell = HandlerUtil.getActiveWorkbenchWindowChecked(event).getShell();
		ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().getSelection();
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(shell);
		try {
			dialog.run(true, true, new IRunnableWithProgress() {

				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					monitor.beginTask("正在扫描处理中...", IProgressMonitor.UNKNOWN);
					// 执行生成任务（耗时）
					GenerationResult result = doGenerateWork(selection, monitor);
					// 关闭loading
					monitor.done();
					// 如果未取消的情况下，显示结果
					if (!monitor.isCanceled()) {
						if (result.error != null) {
							Display.getDefault().asyncExec(() -> alert(result.error));
						} else {
							showSQL(shell, result.data);
						}
					}
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static class GenerationResult {
		String error;
		String data;
	}

	private GenerationResult doGenerateWork(ISelection selection, IProgressMonitor monitor) {
		GenerationResult result = new GenerationResult();
		try {
			List<String> sqlList = generateSQL(selection, monitor);
			if (sqlList.isEmpty()) {
				result.error = "请选择Java实体类或所在包";
			} else {
				result.data = String.join("\n\n", sqlList);
			}
		} catch (SQLGenerationException ex) {
			result.error = ex.getMessage();
		} catch (Exception ex) {
			result.error = "生成SQL失败，请确保项目编译通过，并且是效的实体类";
			ex.printStackTrace();
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private List<String> generateSQL(ISelection selection, IProgressMonitor monitor)
			throws SQLGenerationException, JavaModelException {
		if (!(selection instanceof IStructuredSelection)) {
			return Collections.emptyList();
		}
		GenerationProcessor processor = new GenerationProcessor(monitor);
		Iterator<Object> iter = ((IStructuredSelection) selection).iterator();
		while (iter.hasNext()) {
			Object element = iter.next();
			if (element instanceof ICompilationUnit) {
				processor.process((ICompilationUnit) element);
			} else if (element instanceof IPackageFragment) {
				IPackageFragment selectedPackage = (IPackageFragment) element;
				IPackageFragmentRoot root = (IPackageFragmentRoot) selectedPackage.getParent();
				processor.process(root, selectedPackage);
			} else if (element instanceof IPackageFragmentRoot) {
				processor.process((IPackageFragmentRoot) element, null);
			}
		}
		processor.finish();
		return processor.getResult();
	}

	private void alert(String msg) {
		MessageBox messageBox = new MessageBox(Display.getDefault().getActiveShell(), SWT.OK);
		messageBox.setMessage(msg);
		messageBox.open();
	}

	private void showSQL(Shell shell, String sql) {
		// 在UI线程中执行
		shell.getDisplay().asyncExec(() -> {
			// 创建对话框
			Shell dialogShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
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

			// 获取父 Shell 的大小和位置
			Rectangle parentSize = shell.getBounds();
			Rectangle dialogSize = dialogShell.getBounds();

			// 计算新对话框的位置，使其居中
			int locationX = parentSize.x + (parentSize.width - dialogSize.width) / 2;
			int locationY = parentSize.y + (parentSize.height - dialogSize.height) / 2;

			// 设置对话框的位置
			dialogShell.setLocation(locationX, locationY);

			dialogShell.open();
		});
	}

	private class GenerationProcessor {
		IProgressMonitor monitor;
		List<String> sqlList = new ArrayList<>();
		IPackageFragmentRoot root;
		List<IPackageFragment> selectedPackages = new ArrayList<>();
		Set<ICompilationUnit> processedUnits = new HashSet<>();

		public GenerationProcessor(IProgressMonitor monitor) {
			super();
			this.monitor = monitor;
		}

		public List<String> getResult() {
			return sqlList;
		}

		public void process(ICompilationUnit unit) throws SQLGenerationException, JavaModelException {
			if (monitor.isCanceled()) {
				return;
			}
			if (!processedUnits.add(unit)) {
				return;
			}
			for (IType type : unit.getAllTypes()) {
				String sql = generateSQLByJavaClass(type);
				if (sql != null) {
					sqlList.add(sql);
				}
			}
		}

		public void process(IPackageFragmentRoot root, IPackageFragment selectedPackage) {
			this.root = root;
			if (selectedPackage != null) {
				selectedPackages.add(selectedPackage);
			}
		}

		public void finish() throws SQLGenerationException, JavaModelException {
			if (root == null) {
				return;
			}
			Set<String> selectedPackageNames = selectedPackages.stream().map(IPackageFragment::getElementName)
					.collect(Collectors.toSet());
			List<String> selectedPrefixes = selectedPackages.stream().map(i -> i + ".").collect(Collectors.toList());
			for (IJavaElement child : root.getChildren()) {
				if (monitor.isCanceled()) {
					break;
				}
				if (child instanceof IPackageFragment) {
					IPackageFragment pkg = (IPackageFragment) child;
					if (isSelectedPackage(pkg, selectedPackageNames, selectedPrefixes)) {
						iteratePackage(pkg);
					}
				}
			}
		}

		private boolean isSelectedPackage(IPackageFragment subPackage, Set<String> selectedPackageNames,
				List<String> selectedPrefixes) {
			// 空表示全选
			if (selectedPackageNames.isEmpty()) {
				return true;
			}
			if (selectedPackageNames.contains(subPackage.getElementName())) {
				return true;
			}
			for (String prefix : selectedPrefixes) {
				if (subPackage.getElementName().startsWith(prefix)) {
					return true;
				}
			}
			return false;
		}

		private void iteratePackage(IPackageFragment pkg) throws SQLGenerationException, JavaModelException {
			for (IJavaElement child : pkg.getChildren()) {
				if (monitor.isCanceled()) {
					break;
				}
				if (child.getElementType() == IJavaElement.COMPILATION_UNIT) {
					process((ICompilationUnit) child);
				}
			}
		}

		private String generateSQLByJavaClass(IType type) throws SQLGenerationException, JavaModelException {
			IAnnotation entityAnnotation = getAnnotation(type, "Entity");
			if (entityAnnotation == null) {
				return null;
			}
			IAnnotation tableAnnotation = getAnnotation(type, "Table");
			if (tableAnnotation == null) {
				return null;
			}
			// 表定义
			TableDefinition def = new TableDefinition();
			def.setName(AnnotationUtils.getStringValue(tableAnnotation, "name"));
			def.setComment(AnnotationUtils.getStringValue(tableAnnotation, "comment"));
			if (StringUtil.isEmpty(def.getComment())) {
				IAnnotation apiModelAnnotation = getAnnotation(type, "ApiModel");
				if (apiModelAnnotation != null) {
					def.setComment(AnnotationUtils.getStringValue(apiModelAnnotation, "value"));
				}
			}
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
				if (StringUtil.isEmpty(column.getComment())) {
					IAnnotation apiModelPropsAnnotation = getAnnotation(field, "ApiModelProperty");
					if (apiModelPropsAnnotation != null) {
						column.setComment(AnnotationUtils.getStringValue(apiModelPropsAnnotation, "value"));
					}
				}
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
			char typeChar = fieldTypeSignature.charAt(0);
			// 基本类型的签名是单个字符
			boolean isPrimitive = (typeChar == Signature.C_INT || typeChar == Signature.C_BOOLEAN
					|| typeChar == Signature.C_BYTE || typeChar == Signature.C_CHAR || typeChar == Signature.C_DOUBLE
					|| typeChar == Signature.C_FLOAT || typeChar == Signature.C_LONG || typeChar == Signature.C_SHORT);
			if (isPrimitive) {
				return fullyQualifiedTypeName;
			}
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
			return Stream.of(type.getFields()).filter(field -> getAnnotation(field, "Id") != null).findAny()
					.orElse(null);
		}

		private String getFullyQualifiedName(String typeSignature, IType type) throws JavaModelException {
			String[][] resolvedTypeNames = type.resolveType(Signature.toString(typeSignature));
			if (resolvedTypeNames != null && resolvedTypeNames.length > 0) {
				// 取第一个解析结果，它通常是正确的
				String[] typeNameParts = resolvedTypeNames[0];
				return typeNameParts[0] + "." + typeNameParts[1]; // 包名.类名
			} else {
				return Signature.toString(typeSignature); // 如果无法解析，则返回原始类型签名
			}
		}

		private IAnnotation getAnnotation(IAnnotatable annotatable, String name) {
			IAnnotation annotation = annotatable.getAnnotation(name);
			if (annotation == null || !annotation.exists()) {
				return null;
			}
			return annotation;
		}

	}

}
