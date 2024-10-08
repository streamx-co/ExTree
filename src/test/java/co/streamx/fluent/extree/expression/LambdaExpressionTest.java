package co.streamx.fluent.extree.expression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.danekja.java.misc.serializable.SerializableRunnable;
import org.danekja.java.util.function.serializable.*;
import org.junit.Test;

import co.streamx.fluent.extree.Customer;
import co.streamx.fluent.extree.Fluent;
import co.streamx.fluent.extree.Person;

@SuppressWarnings("serial")
public class LambdaExpressionTest implements Serializable {

    private static <T> Predicate<T> ensureSerializable(SerializablePredicate<T> x) {
        return x;
    }

    // @Test
    // public void testGetBody() {
    // fail("Not yet implemented");
    // }
    //
    // @Test
    // public void testGetParameters() {
    // fail("Not yet implemented");
    // }

    @Test
    public void testParseNew() throws Throwable {
        Predicate<java.util.Date> pp1 = new SerializablePredicate<Date>() {

            @Override
            public boolean test(Date t) {
                // TODO Auto-generated method stub
                return false;
            }
        };
        Class<? extends Predicate> class1 = pp1.getClass();
        class1.getName();
        SerializablePredicate<java.util.Date> pp = d -> d.after(new java.sql.Time(System.currentTimeMillis()));
        LambdaExpression<Predicate<java.util.Date>> le = LambdaExpression.parse(pp);
        Function<Object[], ?> fr = le.compile();

        le.toString();

        Date anotherDate = new Date(System.currentTimeMillis() + 1000);
        assertEquals(pp.test(anotherDate), fr.apply(new Object[]{anotherDate}));

        pp = d -> d.compareTo(anotherDate) < 10;
        le = LambdaExpression.parse(pp);

        fr = le.compile();

        Date date = new Date();
        assertEquals(pp.test(date), fr.apply(new Object[]{date}));
        // Predicate<java.util.Date> le = LambdaExpression.parse(pp);
        // le = LambdaExpression.parse(pp).compile();
        //
        // assertTrue(le.invoke(new java.sql.Date(System.currentTimeMillis()
        // + (5 * 1000))));
        // assertFalse(le.invoke(new java.sql.Date(System.currentTimeMillis()
        // - (5 * 1000))));
    }

    @Test
    public void testParseP() throws Throwable {
        SerializablePredicate<Float> pp = t -> t > 6 ? t < 12 : t > 2;
        LambdaExpression<Predicate<Float>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(4f), le.apply(new Object[]{4f}));
        assertEquals(pp.test(7f), le.apply(new Object[]{7f}));
        assertEquals(pp.test(14f), le.apply(new Object[]{14f}));
        assertEquals(pp.test(12f), le.apply(new Object[]{12f}));
        assertEquals(pp.test(6f), le.apply(new Object[]{6f}));
        assertEquals(pp.test(Float.NaN), le.apply(new Object[]{Float.NaN}));
    }

    @Test
    public void testParseP1() throws Throwable {
        Predicate<String> pp = ensureSerializable(t -> t.equals("abc"));
        LambdaExpression<Predicate<String>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test("abc"), le.apply(new Object[]{"abc"}));
        assertEquals(pp.test("abC"), le.apply(new Object[]{"abC"}));
    }

    @Test
    public void testParseP2() throws Throwable {
        final Object[] ar = new Object[]{5};

        SerializablePredicate<Integer> pp = t -> (ar.length << t) == (1 << 5) && ar[0] instanceof Number;

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        parsed.toString();
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(4), le.apply(new Object[]{4}));
    }

    @Test
    public void testParseThisIsNonNull() throws Throwable {

        SerializablePredicate<Integer> pp = t -> this != null;

        LambdaExpression<Predicate<Integer>> lambda = LambdaExpression.parse(pp);

        Function<Object[], ?> le = lambda.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
    }

    @Test
    public void testParseThisIsNull() throws Throwable {

        SerializablePredicate<Integer> pp = t -> this == null;

        LambdaExpression<Predicate<Integer>> lambda = LambdaExpression.parse(pp);

        Function<Object[], ?> le = lambda.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
    }

    @Test
    public void testParseP3() throws Throwable {
        final Object[] ar = new Object[]{5f};

        Predicate<Integer> pp = ensureSerializable(t -> ar[0] instanceof Float || (ar.length << t) == (1 << 5));

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(4), le.apply(new Object[]{4}));
    }

    @Test
    public void testParseField() throws Throwable {
        SerializablePredicate<Object[]> pp = t -> t.length == 3;

        LambdaExpression<Predicate<Object[]>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        Integer[] ar1 = {2, 3, 4};
        Integer[] ar2 = {2, 4};

        assertEquals(pp.test(ar1), le.apply(new Object[]{ar1}));
        assertEquals(pp.test(ar2), le.apply(new Object[]{ar2}));
    }

    @Test
    public void testParse0() throws Throwable {
        SerializableSupplier<Float> pp = () -> 23f;

        LambdaExpression<Supplier<Float>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertTrue(23f == (Float) le.apply(null));
        assertFalse(24f == (Float) le.apply(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseIllegal() throws Throwable {

        try {
            final Object[] x = new Object[1];
            SerializableSupplier<Float> pp = () -> {
                x[0] = null;
                return 23f;
            };
            LambdaExpression<Supplier<Float>> parsed = LambdaExpression.parse(pp);
            Function<Object[], ?> le = parsed.compile();

            le.apply(null);
        } catch (Throwable e) {
            assertTrue(e.getMessage().indexOf("AASTORE") >= 0);
            throw e;
        }
    }

    @Test
    public void testParse2() throws Throwable {
        SerializableBiFunction<Float, Float, Boolean> pp = (Float t,
                                                            Float r) -> t > 6 ? r < 12 : t > 2;

        LambdaExpression<BiFunction<Float, Float, Boolean>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.apply(7f, 10f), le.apply(new Object[]{7f, 10f}));
        assertEquals(pp.apply(7f, 14f), le.apply(new Object[]{7f, 14f}));
    }

    @Test
    public void testParse4() throws Throwable {
        Predicate<Integer> pp = ensureSerializable(r -> (r < 6 ? r > 1 : r < 4) || (r instanceof Number));

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
    }

    @Test
    public void testParse5() throws Throwable {
        SerializablePredicate<Integer> pp = r -> (r < 6 ? r > 1 : r < 4) || (r > 25 ? r > 28 : r < 32)
                || (r < 23 ? r > 15 : r < 17);

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
        assertEquals(pp.test(29), le.apply(new Object[]{29}));
        assertEquals(pp.test(26), le.apply(new Object[]{26}));
        assertEquals(pp.test(18), le.apply(new Object[]{18}));
        assertEquals(pp.test(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testParse6() throws Throwable {
        Predicate<Integer> pp = ensureSerializable(
                r -> (r < 6 ? r > 1 : r < 4) && (r > 25 ? r > 28 : r < 32) || (r < 23 ? r > 15 : r < 17));

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
        assertEquals(pp.test(29), le.apply(new Object[]{29}));
        assertEquals(pp.test(26), le.apply(new Object[]{26}));
        assertEquals(pp.test(18), le.apply(new Object[]{18}));
        assertEquals(pp.test(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testParse7() throws Throwable {
        SerializablePredicate<Integer> pp = r -> (r < 6 && r > 25) || r < 23;

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
        assertEquals(pp.test(29), le.apply(new Object[]{29}));
        assertEquals(pp.test(26), le.apply(new Object[]{26}));
        assertEquals(pp.test(18), le.apply(new Object[]{18}));
        assertEquals(pp.test(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testParse8() throws Throwable {
        SerializablePredicate<Integer> pp = r -> (r < 6 || r > 25) && r < 23;

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
        assertEquals(pp.test(29), le.apply(new Object[]{29}));
        assertEquals(pp.test(26), le.apply(new Object[]{26}));
        assertEquals(pp.test(18), le.apply(new Object[]{18}));
        assertEquals(pp.test(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testParse9() throws Throwable {
        SerializablePredicate<Integer> pp = r -> (r < 6 || r > 25) && r < 23 || r > 25;

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
        assertEquals(pp.test(29), le.apply(new Object[]{29}));
        assertEquals(pp.test(26), le.apply(new Object[]{26}));
        assertEquals(pp.test(18), le.apply(new Object[]{18}));
        assertEquals(pp.test(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testParse10() throws Throwable {
        SerializableFunction<Integer, Integer> pp = r -> ~r;

        LambdaExpression<Function<Integer, Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.apply(5), le.apply(new Object[]{5}));
        assertEquals(pp.apply(-10), le.apply(new Object[]{-10}));
        assertEquals(pp.apply(29), le.apply(new Object[]{29}));
        assertEquals(pp.apply(26), le.apply(new Object[]{26}));
        assertEquals(pp.apply(-18), le.apply(new Object[]{-18}));
        assertEquals(pp.apply(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testParse11() throws Throwable {
        SerializableFunction<Integer, Byte> pp = r -> (byte) (int) r;

        LambdaExpression<Function<Integer, Byte>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.apply(5), le.apply(new Object[]{5}));
        assertEquals(pp.apply(-10), le.apply(new Object[]{-10}));
        assertEquals(pp.apply(29), le.apply(new Object[]{29}));
        assertEquals(pp.apply(26), le.apply(new Object[]{26}));
        assertEquals(pp.apply(-18), le.apply(new Object[]{-18}));
        assertEquals(pp.apply(144567), le.apply(new Object[]{144567}));
        assertEquals(pp.apply(-144567), le.apply(new Object[]{-144567}));
    }

    @Test
    public void testMethodRefInstance() throws Throwable {
        SerializableBiPredicate<Person, Person> pp = Object::equals;
        LambdaExpression<?> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> compiled = parsed.compile();
        Person person = new Person();
        person.setName("John");
        assertEquals(pp.test(person, person), compiled.apply(new Object[]{person, person}));
        assertEquals(pp.test(person, new Person()), compiled.apply(new Object[]{person, new Person()}));
    }

    @Test
    public void testMethodRef() throws Throwable {
        SerializableFunction<Customer, Integer> pp = Customer::getData;

        LambdaExpression<Function<Customer, Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        Customer c = new Customer(5);

        assertEquals(pp.apply(c), le.apply(new Object[]{c}));

        pp = (Customer c1) -> c1.getData();

        parsed = LambdaExpression.parse(pp);
        le = parsed.compile();

        assertEquals(pp.apply(c), le.apply(new Object[]{c}));

        Fluent<Customer> f = new Fluent<Customer>();
        f.property(Customer::getData);

        assertEquals("public int co.streamx.fluent.extree.Customer.getData()", f.getMember().toString());

        le = f.getParsed().compile();

        assertEquals(pp.apply(c), le.apply(new Object[]{c}));
    }

    @Test
    public void testMethodRef2() throws Throwable {
        LambdaExpression<SerializableFunction<Person, SerializableFunction<Person, Integer>>> parsed = LambdaExpression
                .parseMethod(this::getFunction);
        Function<Object[], Function<Object[], ?>> compiled = (Function<Object[], Function<Object[], ?>>) parsed
                .compile();

        Person p = new Person();
        p.setAge(1);
        p.setHeight(2);
        Function<Object[], ?> delegate = compiled.apply(new Object[]{p});
        assertEquals(getFunction(p).apply(p), delegate.apply(new Object[]{p}));

        p.setHeight(200);
        delegate = compiled.apply(new Object[]{p});
        assertEquals(getFunction(p).apply(p), delegate.apply(new Object[]{p}));
    }

    @Test
    public void testMethodRef3() throws Throwable {
        Person p = new Person();
        p.setAge(1);
        LambdaExpression<SerializableSupplier<Integer>> parsed = LambdaExpression.parseMethod(p::getAge);
        Function<Object[], ?> compiled = parsed.compile();

//        Object delegate = compiled.apply(new Object[]{});
        assertEquals(p.getAge(), compiled.apply(new Object[]{}));

//        p.setHeight(200);
//        delegate = compiled.apply(new Object[] { p });
//        assertEquals(getFunction(p).apply(p), delegate.apply(new Object[] { p }));
    }

    @Test
    public void testMethodRef4() throws Throwable {

        SerializableFunction<Person, Integer> pp = p -> p.getParent().getHeight();

        var parsed = LambdaExpression.parseMethod(pp);
        Function<Object[], ?> compiled = parsed.compile();

        Person person = new Person();
        person.setParent(new Person());
        assertEquals(pp.apply(person), compiled.apply(new Object[]{person}));
    }

    @Test
    public void testMethodRef41() throws Throwable {
        
        SerializableRunnable r = () -> {
            SerializableFunction<Person, Integer> pp = p -> p.getParent().getHeight();
            Person person = new Person();
            person.setParent(new Person());
            System.out.println("Hello1 \2, good \1:" + pp.apply(person) + "!!" + System.currentTimeMillis() + System.getProperty("user.name"));
        };


        var parsed = LambdaExpression.parseMethod(r);
        System.out.println(parsed);
        r.run();
    }

    @Test
    public void testMethodRef5() throws Throwable {
        Person p = new Person();
        p.setParent(new Person());
        p.getParent().setAge(1);
        SerializableSupplier<Integer> supplier = () -> p.getParent().getAge();
        var parsed = LambdaExpression.parseMethod(supplier);
        Function<Object[], ?> compiled = parsed.compile();

        assertEquals(supplier.get(), compiled.apply(new Object[]{}));
    }

    @Test(expected = NullPointerException.class)
    public void testParse12() throws Throwable {
        SerializableFunction<Integer, Byte> pp = r -> (byte) (int) r;

        LambdaExpression<Function<Integer, Byte>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        le.apply(null);
    }

    @Test
    public void canToStringACompoundExpression() throws Exception {
        SerializableFunction<String, String> e = s -> s.substring(0, 1).toUpperCase();
        Expression body = LambdaExpression.parse(e).getBody();
        assertEquals("P0.substring(0, 1).toUpperCase()", body.toString());
    }

    @Test
    public void canParseAnExpressionWhereCharIsPromotedToIntAsAMethodParameter() throws Exception {
        SerializableFunction<String, Integer> e = s -> Math.abs(s.charAt(0));
        LambdaExpression<Function<String, Integer>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void canParseAnExpressionWhereCharIsPromotedToLongAsAMethodParameter() throws Exception {
        SerializableFunction<String, Long> e = s -> Math.abs((long) s.charAt(0));
        LambdaExpression<Function<String, Long>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void canParseAnExpressionWhereCharIsPromotedToFloatAsAMethodParameter() throws Exception {
        SerializableFunction<String, Float> e = s -> Math.abs((float) s.charAt(0));
        LambdaExpression<Function<String, Float>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void canParseAnExpressionWhereCharIsPromotedToIntAsAnOperand() throws Exception {
        SerializableFunction<String, Integer> e = s -> s.charAt(0) + 1;
        LambdaExpression<Function<String, Integer>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void testExpression1() {
        SerializablePredicate<Person> p = t -> t.getName() == "Maria Bonita";
        final LambdaExpression<Predicate<Person>> ex = LambdaExpression.parse(p);
        assertNotNull(ex);

        Function<Object[], ?> le = ex.compile();

        Person t = new Person();
        t.setName("Maria Bonita");
        assertEquals(p.test(t), le.apply(new Object[]{t}));
    }

    @Test
    public void testExpression2() {
        this.testExpression(t -> t.getName() == "Maria Bonita", "Maria Bonita");
    }

    @Test
    public void testExpression3() {
        final String name = "Maria Bonita";
        this.testExpression(name);
    }

    @Test
    public void testExpression4() {
        this.testExpression("Maria Bonita");
    }

    protected void testExpression(final String name) {
        this.testExpression(t -> t.getName() == name, name);
    }

    protected void testExpression(SerializablePredicate<Person> p,
                                  String name) {
        final LambdaExpression<Predicate<Person>> ex = LambdaExpression.parse(p);
        assertNotNull(ex);

        Function<Object[], ?> le = ex.compile();

        Person t = new Person();
        t.setName(name);
        assertEquals(p.test(t), le.apply(new Object[]{t}));
    }

    @Test
    public void testWithScopedConstants() {
        int age = 90;
        double height = 200;
        float height1 = 0;
        SerializablePredicate<Person> p = (Person person) -> {
            return person.getAge() == age && person.getHeight() > (height + height1);
        };
        Person t = new Person();
        p.test(t);
        LambdaExpression<Predicate<Person>> ex = LambdaExpression.parse(p);

        Function<Object[], ?> le = ex.compile();

        assertEquals(p.test(t), le.apply(new Object[]{t}));

        t.setAge(age);
        assertEquals(p.test(t), le.apply(new Object[]{t}));

        t.setHeight((int) height + 1);
        assertEquals(p.test(t), le.apply(new Object[]{t}));
    }

    @Test
    public void composition1() {
        SerializablePredicate<Person> predicate1 = person -> person.getAge() > 18;
        SerializablePredicate<Person> predicate2 = person -> person.getName().equals("Bob");
        Predicate<Person> p = predicate1.and(predicate2);

        Person t = new Person();
        p.test(t);
        LambdaExpression<Predicate<Person>> ex = LambdaExpression.parse(p);

        Function<Object[], ?> le = ex.compile();

        assertEquals(p.test(t), le.apply(new Object[]{t}));

        t.setName("Bob");
        assertEquals(p.test(t), le.apply(new Object[]{t}));

        t.setAge(20);
        assertEquals(p.test(t), le.apply(new Object[]{t}));
    }

    @Test
    public void composition2() throws Exception {
        SerializableFunction<String, Integer> e = s -> s.charAt(0) + 1;
        e = e.andThen(i -> i + 4);
        LambdaExpression<Function<String, Integer>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void composition3() throws Exception {
        SerializableFunction<String, Integer> e = s -> s.charAt(0) + 1;
        e = e.andThen(i -> i + 4);
        e = e.andThen(i -> i + 5);
        LambdaExpression<Function<String, Integer>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void composition4() throws Exception {
        SerializablePredicate<Person> person = Person::isAdult;
        SerializablePredicate<Person> personAnd = person.and(x -> true);
        LambdaExpression<SerializablePredicate<Person>> lambda = LambdaExpression.parse(personAnd);

        Function<Object[], ?> le = lambda.compile();

        Person t = new Person();
        assertEquals(personAnd.test(t), le.apply(new Object[]{t}));

        t.setAge(20);
        assertEquals(personAnd.test(t), le.apply(new Object[]{t}));
    }

    @Test
    public void composition5() throws Exception {
        long age = 20;
        SerializablePredicate<Person> person = p -> p.getAge() == age;
        SerializablePredicate<Person> personAnd = person.and(x -> true);
        LambdaExpression<SerializablePredicate<Person>> lambda = LambdaExpression.parse(personAnd);

        Function<Object[], ?> le = lambda.compile();

        Person t = new Person();
        assertEquals(personAnd.test(t), le.apply(new Object[]{t}));

        t.setAge(20);
        assertEquals(personAnd.test(t), le.apply(new Object[]{t}));
    }

    @Test
    public void composition2NotSerializable() throws Exception {
        SerializableFunction<String, Integer> e = s -> s.charAt(0) + 1;
        e = e.andThen(i -> i + 4);
        LambdaExpression<Function<String, Integer>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void composition3NotSerializable() throws Exception {
        SerializableFunction<String, Integer> e = s -> s.charAt(0) + 1;
        e = e.andThen(i -> i + 4);
        e = e.andThen(i -> i + 5);
        LambdaExpression<Function<String, Integer>> parsed = LambdaExpression.parse(e);

        Function<Object[], ?> le = parsed.compile();

        assertEquals(e.apply("A"), le.apply(new Object[]{"A"}));
    }

    @Test
    public void partialApplication0() throws Exception {

        SerializableFunction<Integer, Integer> e = x -> x + 3;

        LambdaExpression<Function<Integer, Integer>> parsed = LambdaExpression.parse(e);

        LambdaExpression<?> lambda = Expression.lambda(Integer.class, parsed, Collections.emptyList(),
                Collections.emptyList(), null);

        Function<Object[], Function<Object[], ?>> compiled = (Function<Object[], Function<Object[], ?>>) lambda
                .compile();
        Function<Object[], ?> applied0 = compiled.apply(new Object[0]);
        Object applied = applied0.apply(new Object[]{3});

        assertEquals(e.apply(3), applied);
    }

    @Test
    public void partialApplication1() throws Exception {

        Number f = 56;

        SerializableFunction<Short, SerializableBiFunction<Float, Character, Float>> e = y -> (x,
                                                                                               z) -> y / x - z
                + f.floatValue()
                + 3
                - getSomething();

        LambdaExpression<Function<Short, SerializableBiFunction<Float, Character, Float>>> parsed = LambdaExpression
                .parse(e);

        Function<Object[], Function<Object[], ?>> compiled = (Function<Object[], Function<Object[], ?>>) parsed
                .compile();

        Function<Object[], ?> a1 = compiled.apply(new Object[]{(short) 23});
        Object a2 = a1.apply(new Object[]{1.2f, 'g'});

        assertEquals(e.apply((short) 23).apply(1.2f, 'g'), a2);
    }

    @Test
    public void partialApplication2() throws Exception {

        Number f = 56;

        SerializableFunction<Short, BiFunction<Float, Character, Function<Integer, Float>>> e = y -> (x,
                                                                                                      z) -> (m) -> y / x - z
                + f.floatValue() + 3
                - getSomething() + m;

        var parsed = LambdaExpression.parse(e);

        var syntheticRemover = new SimpleExpressionVisitor() {
            @Override
            public Expression visit(LambdaExpression<?> e) {
                return super.visit(e.parseMethodRef());
            }
        };


        parsed = (LambdaExpression<SerializableFunction<Short, BiFunction<Float, Character, Function<Integer, Float>>>>) parsed.accept(syntheticRemover);

        var compiled = (Function<Object[], Function<Object[], Function<Object[], ?>>>) parsed.compile();

        var a1 = compiled.apply(new Object[]{(short) 23});
        var a2 = a1.apply(new Object[]{1.2f, 'g'});
        var a3 = a2.apply(new Object[]{153});

        assertEquals(e.apply((short) 23).apply(1.2f, 'g').apply(153), a3);
    }

    @Test
    public void testBlock() throws Exception {

        SerializableBiFunction<?, ?, Integer> e = (Person p,
                                                   Person p2) -> {
            f1(p);
            return f2(p2.getAge() == 3);
        };

        LambdaExpression<SerializableBiFunction<?, ?, Integer>> parsed = LambdaExpression.parse(e);
        Expression body = parsed.getBody();
        assertTrue(body instanceof BlockExpression);

        List<Expression> expressions = ((BlockExpression) body).getExpressions();
        assertEquals(2, expressions.size());
    }

    @Test
    public void testVarargsMethod() {

        String height = "200";
        String height0 = null;
        String height1 = "0";
        String age = "90";
        SerializableSupplier<String> p = () -> {
            return join(age, height0, height, height1);
        };

        LambdaExpression<Supplier<String>> ex = LambdaExpression.parse(p);

        Function<Object[], ?> le = ex.compile();

        assertEquals(p.get(), le.apply(new Object[]{}));

    }

    @Test
    public void testIntVarargsMethod() {

        int height = 200;
        Short height0 = 1;
        int height1 = 0;
        double age = 90;
        SerializableSupplier<Integer> p = () -> {
            return join((int) age, height0, height, height1);
        };

        LambdaExpression<Supplier<Integer>> ex = LambdaExpression.parse(p);

        Function<Object[], ?> le = ex.compile();

        assertEquals(p.get(), le.apply(new Object[]{}));

    }

    public static String join(String... strings) {
        return String.join("-", strings);
    }

    public static int join(int... ints) {
        return IntStream.of(ints).sum();
    }

    public static int f1(Person p) {
        throw new UnsupportedOperationException();
    }

    public static int f2(boolean b) {
        throw new UnsupportedOperationException();
    }

    private SerializableFunction<Person, Integer> getFunction(Person p) {
        if (p.getHeight() > 150) {
            return Person::getHeight;
        }
        return Person::getAge;
    }

    private int getSomething() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Test
    public void testLocals1() throws Throwable {
        SerializablePredicate<Integer> pp = r -> {
            int x = r - 1;
            int y = x + 1;
            return (y < 6 || x > 25) && x < 23;
        };

        LambdaExpression<Predicate<Integer>> parsed = LambdaExpression.parse(pp);
        Function<Object[], ?> le = parsed.compile();

        assertEquals(pp.test(5), le.apply(new Object[]{5}));
        assertEquals(pp.test(11), le.apply(new Object[]{11}));
        assertEquals(pp.test(29), le.apply(new Object[]{29}));
        assertEquals(pp.test(26), le.apply(new Object[]{26}));
        assertEquals(pp.test(18), le.apply(new Object[]{18}));
        assertEquals(pp.test(14), le.apply(new Object[]{14}));
    }

    @Test
    public void testGetExpressionType() {

        SerializableSupplier<Long> currentTimeMillis = System::currentTimeMillis;

        LambdaExpression<Supplier<Long>> parsed = LambdaExpression.parse(currentTimeMillis);

        System.out.println(parsed.compile().apply(new Object[0]));

    }

    static int invocations = 0;

    public static void method() {
        invocations++;
    }

    @FunctionalInterface
    interface Callable extends Serializable {
        void call();
    }

    @Test
    public void testIssue5() {
        Callable callable1 = () -> method();
        Callable callable2 = LambdaExpressionTest::method;
        var expression1 = LambdaExpression.parse(callable1);
        var expression2 = LambdaExpression.parse(callable2);

        expression1.compile().apply(new Object[0]);
        expression2.compile().apply(new Object[0]);

        assertEquals(2, invocations);
    }

    // @Test
    // public void testGetResultType() {
    // fail("Not yet implemented");
    // }

}
