package co.streamx.fluent.extree;

import java.io.Serializable;
import java.lang.reflect.Member;
import java.util.function.Function;

import co.streamx.fluent.extree.expression.*;
import lombok.Getter;

@Getter
public class Fluent<T> {

    public interface Property<T, R> extends Function<T, R>, Serializable {
    }

    @Getter
    private static class MemberExtractor extends SimpleExpressionVisitor {
        private MemberExpression memberExpression;

        @Override
        public Expression visit(MemberExpression e) {
            memberExpression = e;
            return e;
        }
    }

    private LambdaExpression<Function<T, ?>> parsed;
    private Member member;

    public Fluent<T> property(Property<T, ?> propertyRef) {
        LambdaExpression<Function<T, ?>> parsed = LambdaExpression
                .parse(propertyRef);
        var visitor = new MemberExtractor();
        parsed.accept(visitor);

        member = visitor.getMemberExpression().getMember();
        this.parsed = parsed;

        return this;
    }
}
