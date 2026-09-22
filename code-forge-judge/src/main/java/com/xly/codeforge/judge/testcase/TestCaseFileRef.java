package com.xly.codeforge.judge.testcase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 真·文件判题（档位 B）的用例文件引用 —— 当前的预留口子，尚未实现。
 *
 * <p>未来若升级沙箱支持「程序直接读 input.txt」（而非 stdin），用例不再是内存字符串，
 * 而是对象存储 key / 本地路径 + 程序侧看到的文件名。{@link FileAwareTestCaseProvider}
 * 负责产出这类引用，判题侧据此把文件物化进沙箱（挂载卷或 {@code fileArgs}）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseFileRef implements Serializable {

    /**
     * 对象存储 key 或本地路径
     */
    private String storageKey;

    /**
     * 程序侧看到的文件名，如 input.txt / output.txt
     */
    private String logicalName;
}
