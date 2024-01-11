package com.sunnysuperman.sqlgenerator.eclipse;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 字符串工具类
 * 
 */
public class StringUtil {
	public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
	public static final String UTF8 = StandardCharsets.UTF_8.name();
	public static final String EMPTY = "";

	protected StringUtil() {
	}

	/**
	 * 用指定的字符串替换某字符串的匹配子串，只替换匹配到的第一个
	 * 
	 * @param text         待替换的字符串
	 * @param searchString 需替换的字符串
	 * @param replacement  用于替换的字符串
	 * @return 替换后的字符串
	 */
	public static String replace(String text, String searchString, String replacement) {
		return replace(text, searchString, replacement, 1);
	}

	/**
	 * 用指定的字符串替换某字符串的所有匹配子串
	 * 
	 * @param text         待替换的字符串
	 * @param searchString 需替换的字符串
	 * @param replacement  用于替换的字符串
	 * @return 替换后的字符串
	 */
	public static String replaceAll(String text, String searchString, String replacement) {
		return replace(text, searchString, replacement, -1);
	}

	/**
	 * 替换字符串，若max为负数，则全文搜索，匹配到的全部替换
	 * 
	 * @param text         待替换的字符串
	 * @param searchString 需替换的字符串
	 * @param replacement  用于替换的字符串
	 * @param max          需替换的个数
	 * @return 替换后的字符串
	 */
	public static String replace(String text, String searchString, String replacement, int max) {
		if (isEmpty(text) || isEmpty(searchString) || replacement == null || max == 0) {
			return text;
		}
		int start = 0;
		int end = text.indexOf(searchString, start);
		if (end == -1) {
			return text;
		}
		int replLength = searchString.length();
		int increase = replacement.length() - replLength;
		increase = (increase < 0 ? 0 : increase);
		increase *= (max < 0 ? 16 : (max > 64 ? 64 : max));
		StringBuilder buf = new StringBuilder(text.length() + increase);
		while (end != -1) {
			buf.append(text.substring(start, end)).append(replacement);
			start = end + replLength;
			if (--max == 0) {
				break;
			}
			end = text.indexOf(searchString, start);
		}
		buf.append(text.substring(start));
		return buf.toString();
	}

	/**
	 * 字符串是否为空
	 * 
	 * @param cs 待判断字符串
	 * @return 若字符串为null或长度为0，则返回true；否则，返回false
	 */
	public static boolean isEmpty(CharSequence cs) {
		return cs == null || cs.length() == 0;
	}

	/**
	 * 字符串是否不为空，此方法和{@code !}{@linkplain #isEmpty(CharSequence)}效果一致
	 * 
	 * @param cs 待判断字符串
	 * @return 若字符串不为null且长度大于0，则返回true；否则，返回false
	 */
	public static boolean isNotEmpty(CharSequence cs) {
		return cs != null && cs.length() > 0;
	}

	/**
	 * 判断字符串是否为空
	 * <p>
	 * 此方法和{@linkplain #isEmpty(CharSequence)}的区别在于，多了一个条件，长度大于0
	 * 的情况下，如果字符串都是空字符，则返回true
	 * 
	 * @param cs 待判断字符串
	 * @return
	 */
	public static boolean isBlank(CharSequence cs) {
		int strLen;
		if (cs == null || (strLen = cs.length()) == 0)
			return true;
		for (int i = 0; i < strLen; i++)
			if (!Character.isWhitespace(cs.charAt(i)))
				return false;

		return true;
	}

	/**
	 * 判断字符串非空，效果等同{@code !}{@linkplain #isBlank(CharSequence)}
	 * 
	 * @param cs 待判断字符串
	 * @return
	 */
	public static boolean isNotBlank(CharSequence cs) {
		return !isBlank(cs);
	}

	public static String or(String s1, String s2) {
		return isNotEmpty(s1) ? s1 : s2;
	}

	/**
	 * 过滤字符串两端的空字符
	 * 
	 * @param str 待过滤字符串
	 * @return 若为null，则返回null；否则，调用{@linkplain String#trim()}
	 */
	public static String trim(String str) {
		return str != null ? str.trim() : null;
	}

	/**
	 * 过滤字符串两端的空字符串，然后调用{@linkplain #isEmpty(CharSequence) isEmpty(str)}
	 * ，若为true，则返回null，否则返回trim后的结果
	 * 
	 * @param str
	 * @return
	 */
	public static String trimToNull(String str) {
		String ts = trim(str);
		return isEmpty(ts) ? null : ts;
	}

	/**
	 * 若字符串为null，则返回空字符串；否则返回{@linkplain String#trim() str.trim()}
	 * 
	 * @param str
	 * @return
	 */
	public static String trimToEmpty(String str) {
		return str != null ? str.trim() : EMPTY;
	}

	/**
	 * 若字符串为null或者字符串长度为0，返回null；否则，返回字符串本身
	 * 
	 * @param str
	 * @return
	 */
	public static String emptyToNull(String str) {
		if (str == null || str.length() == 0) {
			return null;
		}
		return str;
	}

	/**
	 * 若字符串为null，返回空字符串；否则，返回字符串本身
	 * 
	 * @param str
	 * @return
	 */
	public static String nullToEmpty(String str) {
		if (str == null) {
			return EMPTY;
		}
		return str;
	}

	/**
	 * 将驼峰字符串转为下划线字符串，如 userName -> user_name
	 * 
	 * @param s 待转换字符串
	 * @return 下划线字符串
	 */
	public static String camel2underscore(String s) {
		char c;
		int upperSize = 0;
		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			if (Character.isUpperCase(c)) {
				upperSize++;
			}
		}
		if (upperSize == 0) {
			return s;
		}
		StringBuilder buf = new StringBuilder(s.length() + upperSize);
		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			if (Character.isUpperCase(c)) {
				buf.append('_');
				buf.append(Character.toLowerCase(c));
			} else {
				buf.append(c);
			}
		}
		return buf.toString();
	}

}
