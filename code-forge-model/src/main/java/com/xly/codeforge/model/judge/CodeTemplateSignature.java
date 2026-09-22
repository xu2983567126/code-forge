package com.xly.codeforge.model.judge;

import java.util.List;

/**
 * 核心代码模式下的「方法签名」——Solution 模板与驱动代码的唯一真源（瞬时值对象，不落库）。
 *
 * <p>后端据此生成两段字符串：{@code codeTemplate}（展示给用户的 Solution 骨架）与
 * {@code driverCode}（自带 {@code public class Main} 的驱动，由判题链路执行）。本类本身不写入任何表列，
 * 仅作为生成器的输入与「从骨架反解析」的输出，供管理端二次编辑时回填表单。</p>
 *
 * <p>设计取舍：首版仅支持基础类型、一维数组与 {@code List<X>}（X 为基础包装类型），不支持
 * {@code TreeNode}/{@code ListNode} 等自定义类型，也不支持 {@code void} 返回（这类题留作下一批）。</p>
 */
public record CodeTemplateSignature(

    String methodName,
    String returnType,
    List<Param> params) {

    /**
     * 单个参数：类型 + 名称。类型取值见 {@link CodeTemplateGenerator} 的受支持类型白名单。
     */
    public record Param(String type, String name) {}
}
