package com.xly.codeforge.judge.contract;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.judge.strategy.SpecialJudgeExecutor;
import com.xly.codeforge.judge.strategy.VerdictResolver;
import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 沙箱事实 → 判题结论的契约锁。
 *
 * <p>样例是沙箱 {@code /executeCode} 响应的<b>原文</b>，经与线上同一条反序列化路径
 * （{@code JSONUtil.toBean}）落入 {@link ExecuteCodeResponse}，再走 {@link VerdictResolver}。
 * 因此一次跑覆盖两层契约：</p>
 * <ol>
 *   <li><b>字段层</b>：字段名两边写错一个字，数据静默丢失，结论立刻与样例不符
 *       （另有 {@link #回传字段名必须与判题侧DTO对齐()} 做显式名字核对）；</li>
 *   <li><b>规则层</b>：限制与错误的判定顺序、优先级（systemError &gt; compileError &gt; 用例事实）、
 *       事实数不符的处理、截断输出不可信 —— 都是易回归点。</li>
 * </ol>
 *
 * <p>不启 Spring、不连库、不打沙箱：改判题归约逻辑时秒级反馈。改沙箱输出字段或改判定顺序时，
 * 先改 {@code contract/sandbox-facts.golden.json} 再改实现。</p>
 *
 * @see VerdictResolver
 */
class SandboxFactsContractTest {

    private static final String GOLDEN_PATH = "contract/sandbox-facts.golden.json";

    /**
     * 非 SPJ 样例的占位执行器：样例均不带 spjCode，SPJ 分支不会被命中。
     */
    private static final SpecialJudgeExecutor UNUSED_SPJ =
            (input, expected, userOutput, spjCode, spjLanguage) -> VerdictEnum.ACCEPTED;

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void 沙箱事实应归约为预期结论(String name, JSONObject scenario) {
        ExecuteCodeResponse response = JSONUtil.toBean(
                JSONUtil.toJsonStr(scenario.getJSONObject("sandbox")), ExecuteCodeResponse.class);

        JudgeContext context = JudgeContext.builder()
                .judgeCases(toCases(scenario.getJSONArray("cases")))
                .judgeConfig(toConfig(scenario.getJSONObject("judgeConfig")))
                .sandboxCaseResults(response.getCaseResults())
                .compileError(response.getCompileError())
                .systemError(response.getSystemError())
                .spjCode(scenario.getStr("spjCode"))
                .build();

        JudgeInfo info = new VerdictResolver().resolve(context, UNUSED_SPJ);

        assertThat(info.getMessage())
                .as("结论")
                .isEqualTo(verdictValue(scenario.getStr("expectedVerdict")));
        assertThat(info.getCaseResults())
                .as("逐用例明细")
                .extracting(JudgeCaseResult::getStatus)
                .containsExactlyElementsOf(verdictValues(scenario.getJSONArray("expectedCaseVerdicts")));

        if (scenario.containsKey("expectedTime")) {
            assertThat(info.getTime()).as("聚合耗时").isEqualTo(scenario.getLong("expectedTime"));
        }
        if (scenario.containsKey("expectedMemory")) {
            assertThat(info.getMemory()).as("聚合内存").isEqualTo(scenario.getLong("expectedMemory"));
        }
        String errorNeedle = scenario.getStr("expectedErrorMessageContains");
        if (errorNeedle != null) {
            assertThat(info.getCaseResults())
                    .as("失败用例应带出可定位的错误信息")
                    .anySatisfy(caseResult -> assertThat(caseResult.getErrorMessage()).contains(errorNeedle));
        }
    }

    @Test
    @DisplayName("沙箱回传字段名必须与判题侧 DTO 对齐（重命名会让数据静默丢失）")
    void 回传字段名必须与判题侧DTO对齐() {
        JSONObject wireFields = golden().getJSONObject("wireFields");

        assertThat(instanceFieldNames(ExecuteCodeResponse.class))
                .as("ExecuteCodeResponse 少了沙箱会回的字段")
                .containsAll(wireFields.getJSONArray("ExecuteCodeResponse").toList(String.class));
        assertThat(instanceFieldNames(SandboxCaseResult.class))
                .as("SandboxCaseResult 少了沙箱会回的字段")
                .containsAll(wireFields.getJSONArray("SandboxCaseResult").toList(String.class));
    }

    private static JSONObject golden() {
        try (InputStream in = SandboxFactsContractTest.class.getClassLoader().getResourceAsStream(GOLDEN_PATH)) {
            assertThat(in).as("契约样例文件缺失: %s", GOLDEN_PATH).isNotNull();
            return JSONUtil.parseObj(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("读取契约样例失败: " + GOLDEN_PATH, e);
        }
    }

    static Stream<Arguments> scenarios() {
        JSONArray array = golden().getJSONArray("scenarios");
        List<Arguments> arguments = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject scenario = array.getJSONObject(i);
            arguments.add(Arguments.of(scenario.getStr("name"), scenario));
        }
        return arguments.stream();
    }

    private static List<TestCaseData> toCases(JSONArray array) {
        List<TestCaseData> cases = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject node = array.getJSONObject(i);
            cases.add(TestCaseData.builder()
                    .index(node.getInt("index"))
                    .input(node.getStr("input"))
                    .expectedOutput(node.getStr("expectedOutput"))
                    .build());
        }
        return cases;
    }

    private static JudgeConfig toConfig(JSONObject node) {
        return JSONUtil.toBean(node.toString(), JudgeConfig.class);
    }

    private static VerdictEnum verdictValue(String enumName) {
        return VerdictEnum.valueOf(enumName);
    }

    private static List<VerdictEnum> verdictValues(JSONArray array) {
        List<VerdictEnum> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            values.add(verdictValue(array.getStr(i)));
        }
        return values;
    }

    private static List<String> instanceFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toList());
    }
}
