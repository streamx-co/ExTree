package co.streamx.fluent.extree.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Default expression visitor implementation.
 * 
 * 
 */

public abstract class SimpleExpressionVisitor implements ExpressionVisitor<Expression> {

    private final List<List<Expression>> argumentsStack = new ArrayList<>();

    protected List<Expression> getContextArguments() {
        return argumentsStack.isEmpty() ? null : argumentsStack.get(argumentsStack.size() - 1);
    }

    protected List<Expression> popContextArguments() {
        return argumentsStack.remove(argumentsStack.size() - 1);
    }

    protected Expression resolveContextParameter(ParameterExpression p) {
        return resolveContextParameter(p, argumentsStack.size() - 1);
    }

    private Expression resolveContextParameter(ParameterExpression p,
                                               int frame) {
        Expression e = argumentsStack.get(frame).get(p.getIndex());
        if (e instanceof ParameterExpression)
            return frame == 0 ? e : resolveContextParameter((ParameterExpression) e, frame - 1);
        return e;
    }

    protected void pushContextArguments(List<Expression> args) {
        argumentsStack.add(args);
    }

    protected <T extends Expression> List<T> visitExpressionList(List<T> original) {
        if (original != null) {
            List<T> list = null;
            for (int i = 0, n = original.size(); i < n; i++) {
                T t = original.get(i);
                @SuppressWarnings("unchecked")
                T p = t != null ? (T) t.accept(this) : t;
                if (list != null) {
                    list.add(p);
                } else if (p != original.get(i)) {
                    list = new ArrayList<>(n);
                    for (int j = 0; j < i; j++) {
                        list.add(original.get(j));
                    }
                    list.add(p);
                }
            }
            if (list != null) {
                return Collections.unmodifiableList(list);
            }
        }
        return original;
    }

    protected List<Expression> visitLocals(List<Expression> original) {
        return visitExpressionList(original);
    }

    protected List<Expression> visitArguments(List<Expression> original) {
        return visitExpressionList(original);
    }

    protected List<ParameterExpression> visitParameters(List<ParameterExpression> original) {
        return visitExpressionList(original);
    }

    @Override
    public Expression visit(BinaryExpression e) {
        Expression first = e.getFirst();
        Expression visitedFirst = first.accept(this);

        Expression second = e.getSecond();
        Expression visitedSecond = second.accept(this);

        Expression op = e.getOperator();
        Expression visitedOp = op != null ? op.accept(this) : op;

        if (first != visitedFirst || second != visitedSecond || op != visitedOp)
            return Expression.binary(e.getExpressionType(), visitedOp, visitedFirst, visitedSecond);

        return e;
    }

    @Override
    public Expression visit(ConstantExpression e) {
        Object value = e.getValue();
        if (value instanceof Expression) {
            Object newValue = ((Expression) value).accept(this);
            if (value != newValue)
                return Expression.constant(newValue);
        }
        return e;
    }

    @Override
    public Expression visit(InvocationExpression e) {
        List<Expression> arguments = e.getArguments();
        Expression target = e.getTarget();
        boolean visitTargetWithOldArgs = /*
                                          * target.getExpressionType() == ExpressionType.MethodAccess ||
                                          */ target.getExpressionType() == ExpressionType.Delegate;
        boolean cleanArgsStack = false;
        List<Expression> args = arguments;
        if (!visitTargetWithOldArgs) {
            args = visitArguments(arguments);
            pushContextArguments(arguments);
            cleanArgsStack = true;
        }
        try {
            target = target.accept(this);
            if (visitTargetWithOldArgs) {
                args = visitArguments(arguments);
            }

            if (args != arguments || target != e.getTarget()) {
                return invoke((InvocableExpression) target, args, e);
            }
            return e;
        } finally {
            if (cleanArgsStack)
                popContextArguments();
        }
    }

    protected Expression invoke(InvocableExpression target,
                                List<Expression> args,
                                InvocationExpression original) {
        return Expression.invoke((InvocableExpression) target, args);
    }

    @Override
    public Expression visit(LambdaExpression<?> e) {

        List<ParameterExpression> parameters = visitParameters(e.getParameters());
        List<Expression> locals = visitLocals(e.getLocals());

        Expression body = e.getBody().accept(this);

        if (body != e.getBody() || parameters != e.getParameters() || locals != e.getLocals())
            return Expression.lambda(e.getResultType(), body, parameters, locals, e.getKey());

        return e;
    }

    @Override
    public Expression visit(DelegateExpression e) {

        List<ParameterExpression> parameters = visitParameters(e.getParameters());
        if (parameters != e.getParameters())
            return Expression.delegate(e.getResultType(), e.getDelegate(), parameters);

        return e;
    }

    @Override
    public Expression visit(BlockExpression e) {
        List<Expression> expressions = e.getExpressions();
        List<Expression> visitedList = new ArrayList<>(expressions.size());
        boolean changed = false;
        for (Expression s : expressions) {
            Expression visited = s.accept(this);
            if (s != visited)
                changed = true;
            visitedList.add(visited);
        }
        return changed ? Expression.block(e.getResultType(), visitedList) : e;
    }

    @Override
    public Expression visit(MemberExpression e) {
        Expression instance = e.getInstance();
        if (instance != null) {
            List<Expression> contextArguments = popContextArguments();
            try {
                instance = instance.accept(this);
            } finally {
                pushContextArguments(contextArguments);
            }
            if (instance instanceof LambdaExpression<?>)
                return instance;
        }
        List<ParameterExpression> parameters = visitParameters(e.getParameters());
        if (instance != e.getInstance() || parameters != e.getParameters())
            return Expression.member(e.getExpressionType(), instance, e.getMember(), e.getResultType(), parameters);

        return e;
    }

    @Override
    public Expression visit(NewArrayInitExpression newArrayInitExpression) {
        Expression[] initializers = newArrayInitExpression.getInitializers().toArray(new Expression[0]);

        boolean changed = false;
        for (int i = 0; i < initializers.length; i++) {
            Expression e = initializers[i];
            Expression visited = e.accept(this);
            if (e != visited) {
                changed = true;
                initializers[i] = visited;
            }
        }
        return changed ? Expression.newArrayInit(newArrayInitExpression.getComponentType(), Arrays.asList(initializers))
                : newArrayInitExpression;
    }

    @Override
    public Expression visit(ParameterExpression e) {
        return e;
    }

    @Override
    public Expression visit(UnaryExpression e) {
        Expression operand = e.getFirst();
        Expression visitedOp = operand.accept(this);
        if (operand != visitedOp)
            return Expression.unary(e.getExpressionType(), e.getResultType(), visitedOp);

        return e;
    }

}
