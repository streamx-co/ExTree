package co.streamx.fluent.extree.expression;

/**
 * Represents an expression visitor interface.
 * 
 * @param <T> type the visitor methods return after processing.
 * 
 * 
 */
public interface ExpressionVisitor<T> {
    /**
     * Visits the {@link BinaryExpression}.
     * 
     * @param e {@link BinaryExpression} to visit.
     * @return T
     */
    T visit(BinaryExpression e);

    /**
     * Visits the {@link ConstantExpression}.
     * 
     * @param e {@link ConstantExpression} to visit.
     * @return T
     */
    T visit(ConstantExpression e);

    /**
     * Visits the {@link InvocationExpression}.
     * 
     * @param e {@link InvocationExpression} to visit.
     * @return T
     */
    T visit(InvocationExpression e);

    /**
     * Visits the {@link LambdaExpression}.
     * 
     * @param e {@link LambdaExpression} to visit.
     * @return T
     */
    T visit(LambdaExpression<?> e);

    /**
     * Visits the {@link DelegateExpression}.
     * 
     * @param e {@link DelegateExpression} to visit.
     * @return T
     */
    T visit(DelegateExpression e);

    /**
     * Visits the {@link MemberExpression}.
     * 
     * @param e {@link MemberExpression} to visit.
     * @return T
     */
    T visit(MemberExpression e);

    /**
     * Visits the {@link ParameterExpression}.
     * 
     * @param e {@link ParameterExpression} to visit.
     * @return T
     */
    T visit(ParameterExpression e);

    /**
     * Visits the {@link UnaryExpression}.
     * 
     * @param e {@link UnaryExpression} to visit.
     * @return T
     */
    T visit(UnaryExpression e);

    /**
     * Visits the {@link BlockExpression}.
     * 
     * @param e {@link BlockExpression} to visit.
     * @return T
     */
    T visit(BlockExpression e);

    /**
     * Visits the {@link NewArrayInitExpression}.
     * 
     * @param e {@link NewArrayInitExpression} to visit.
     * @return T
     */
    T visit(NewArrayInitExpression e);
}
