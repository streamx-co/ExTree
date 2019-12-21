package co.streamx.fluent.extree.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 */

@SuppressWarnings("serial")
final class ExpressionStack extends ArrayList<Expression> {

	private BranchExpression _parent;
	private boolean _reduced;

    private final List<Expression> ordered = new ArrayList<>(64);

    private static final Expression[] EmptyExpressionArray = new Expression[0];

    void sort(List<Expression> expressions) {

//        Map<Expression, Integer> insertionOrder = new HashMap<>();
//        for (Expression e : expressions)
//            insertionOrder.put(e, ordered.lastIndexOf(e));
//
//        expressions.sort((e1,
//                          e2) -> insertionOrder.get(e1) - insertionOrder.get(e2));

        Expression[] copy = expressions.toArray(EmptyExpressionArray);
        int[] indices = new int[copy.length];
        Integer[] orders = new Integer[copy.length];

        for (int i = 0; i < copy.length; i++) {
            orders[i] = i;
            indices[i] = ordered.lastIndexOf(copy[i]);
        }

        Arrays.sort(orders, (i1,
                             i2) -> indices[i1] - indices[i2]);

        for (int i = 0; i < copy.length; i++)
            expressions.set(i, copy[orders[i]]);

    }

	ExpressionStack() {
		this(null);
	}

	ExpressionStack(BranchExpression parent) {
		_parent = parent;
	}

	BranchExpression getParent() {
		return _parent;
	}

	private void setParent(BranchExpression value) {
		_parent = value;
	}

	boolean isReduced() {
		return _reduced;
	}

	void reduce() {
		_reduced = true;
	}

	void push(Expression item) {
		add(item);
        ordered.add(item);
	}

	int getDepth() {
		return getParent() != null ? getParent().getDepth() : 0;
	}

	Expression pop() {
		Expression obj = peek();
		remove(size() - 1);

		return obj;
	}

	Expression peek() {
		Expression obj = get(size() - 1);

		return obj;
	}

	static final class BranchExpression extends Expression {

		private final Expression _test;
		private final ExpressionStack _true;
		private final ExpressionStack _false;
		private final ExpressionStack _parent;

        BranchExpression(ExpressionStack parent, Expression test) {
			this(parent, test, null, null);
		}

		BranchExpression(ExpressionStack parent, Expression test,
				ExpressionStack trueE, ExpressionStack falseE) {
			super(ExpressionType.Conditional, Void.TYPE);
			_parent = parent;
			_test = test;

			if (trueE != null) {
				_true = trueE;
				_true.setParent(this);
			} else
				_true = new ExpressionStack(this);

			if (falseE != null) {
				_false = falseE;
				_false.setParent(this);
			} else
				_false = new ExpressionStack(this);
		}

		ExpressionStack getTrue() {
			return _true;
		}

		ExpressionStack getFalse() {
			return _false;
		}

		ExpressionStack get(boolean side) {
			return side ? getTrue() : getFalse();
		}

		Expression getTest() {
			return _test;
		}

		ExpressionStack getParent() {
			return _parent;
		}

		int getDepth() {
			return _parent.getDepth() + 1;
		}

		@Override
		protected <T> T visit(ExpressionVisitor<T> v) {
			throw new IllegalStateException();
		}

		@Override
		public String toString() {
			return "(" + getTest().toString() + " ? " + getTrue().toString()
					+ " : " + getFalse().toString() + ")";
		}
	}
}
