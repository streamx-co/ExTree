package co.streamx.fluent.extree.expression;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.List;

import lombok.Getter;

/**
 * Represents accessing a field or method.
 * 
 * 
 */

@Getter
public final class MemberExpression extends InvocableExpression {

    private final Expression instance;
    private final Member member;

    MemberExpression(int expressionType, Expression instance, Member member, Class<?> resultType,
            List<ParameterExpression> params) {
        super(expressionType, resultType, params);

        this.instance = instance;
        this.member = member;
    }

    @Override
    protected <T> T visit(ExpressionVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        Member m = getMember();
        String me = getInstance() != null ? getInstance().toString() : m.getDeclaringClass().getSimpleName();
        return me + "." + (m instanceof Constructor<?> ? "<new>" : m.getName());
    }
}
