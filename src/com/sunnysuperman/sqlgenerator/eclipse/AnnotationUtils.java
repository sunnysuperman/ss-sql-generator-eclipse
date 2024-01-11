package com.sunnysuperman.sqlgenerator.eclipse;

import java.lang.reflect.Array;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.JavaModelException;

public class AnnotationUtils {

	private AnnotationUtils() {
	}

	public static Object getValue(IAnnotation annotation, String key) throws JavaModelException {
		if (annotation == null || !annotation.exists()) {
			return null;
		}
		// 获取注解的所有成员值对
		IMemberValuePair[] memberValuePairs = annotation.getMemberValuePairs();
		for (IMemberValuePair mvp : memberValuePairs) {
			// 比较成员名称是否与需要的键相匹配
			if (key.equals(mvp.getMemberName())) {
				// 获取成员的值
				return mvp.getValue();
			}
		}
		// 如果没有找到对应的键，返回null
		return null;
	}

	public static String getStringValue(IAnnotation annotation, String key) throws JavaModelException {
		Object v = getValue(annotation, key);
		return v == null ? null : v.toString();
	}

	public static int getIntValue(IAnnotation annotation, String key, int defaultValue) throws JavaModelException {
		Object v = getValue(annotation, key);
		return v == null ? defaultValue : Integer.parseInt(v.toString());
	}

	public static boolean getBooleanValue(IAnnotation annotation, String key, boolean defaultValue)
			throws JavaModelException {
		Object v = getValue(annotation, key);
		return v == null ? defaultValue : Boolean.parseBoolean(v.toString());
	}

	public static String[] getStringArrayValue(IAnnotation annotation, String key) throws JavaModelException {
		Object v = getValue(annotation, key);
		if (v == null) {
			return new String[0];
		}
		if (!v.getClass().isArray()) {
			String s = v.toString();
			if (StringUtil.isEmpty(s)) {
				return new String[0];
			}
			return new String[] { s };
		}
		int length = Array.getLength(v);
		String[] array = new String[length];
		for (int i = 0; i < length; i++) {
			Object item = Array.get(v, i);
			array[i] = item != null ? item.toString() : StringUtil.EMPTY;
		}
		return array;
	}
}
