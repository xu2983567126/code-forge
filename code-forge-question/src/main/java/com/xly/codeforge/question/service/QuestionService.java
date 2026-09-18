package com.xly.codeforge.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.question.QuestionQueryRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionAdjacentVO;
import com.xly.codeforge.model.vo.QuestionVO;

/**
 * 题目服务
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public interface QuestionService extends IService<Question> {

    /**
     * 校验
     *
     * @param question
     * @param create
     */
    void validQuestion(Question question, boolean create);

    /**
     * 根据 id 查询题目信息，仅管理员可以查看完整信息
     * @param id
     * @param loginUser
     * @return
     */
    QuestionVO getQuestionVOById(long id, User loginUser);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    // 本项目用不到
    // /**
    //  * 从 ES 查询
    //  *
    //  * @param questionQueryRequest
    //  * @return
    //  */
    // Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);

    /**
     * 获取题目封装
     *
     * @param question
     * @param loginUser
     * @return
     */
    QuestionVO getQuestionVO(Question question, User loginUser);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param loginUser
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, User loginUser);

    /**
     * 随机获取一道题目的封装（题库专题页「随机一题」用）
     * <p>
     * 题库为空、或排除后无剩余题目时返回 null，由调用方决定如何提示，不抛异常
     * ——「题库里只有一道题时点随机」属于正常业务场景，不该报错。
     *
     * @param excludeId 需要排除的题目 id（通常是当前浏览的题），可为 null
     * @param loginUser 当前登录用户
     * @return 随机题目 VO；无可用题目时返回 null
     */
    QuestionVO getRandomQuestionVO(Long excludeId, User loginUser);

    /**
     * 获取相邻题目（题目详情页「上一题 / 下一题」用）
     *
     * @param id        当前题目 id
     * @param loginUser 当前登录用户
     * @return 相邻题目信息；该方向没有题目时对应字段为 null
     */
    QuestionAdjacentVO getAdjacentQuestion(long id, User loginUser);
}
