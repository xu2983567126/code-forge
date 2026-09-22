/**
 * 判题结果类型（后端 JudgeInfo / JudgeCaseResult 的镜像）。
 *
 * 手写而非塞进 generated SDK：本类型与「运行代码」接口同源，且不走 OpenAPI 生成；
 * generated 重生成会把手写内容冲掉，所以单独放这里。
 */
export interface JudgeCaseResult {
  /** 该用例结论：Accepted / Wrong Answer / ...；无期望输出（试运行中性态）时为 null */
  status?: string | null;
  /** 该用例输入（stdin 全文）；与 output/expectedOutput 同源带出，前端不再按序对齐 */
  input?: string | null;
  /** 耗时（ms） */
  time?: number | null;
  /** 内存（KB） */
  memory?: number | null;
  /** 用户程序实际输出（stdout 全文） */
  output?: string | null;
  /** 该用例期望输出（标准答案）；空白用例（试运行自定义）时为 null */
  expectedOutput?: string | null;
  /** 运行时错误明细（进程 stderr）；非运行时错误为空 */
  errorMessage?: string | null;
}

export interface JudgeInfo {
  /** 聚合结论（最终 verdict）；全用例无期望输出（中性态）时为 null */
  message?: string | null;
  time?: number | null;
  memory?: number | null;
  caseResults?: JudgeCaseResult[] | null;
  /** 编译 / 系统错误的原始诊断（编译器 stderr / 异常栈）；常规判定为空 */
  detail?: string | null;
}
