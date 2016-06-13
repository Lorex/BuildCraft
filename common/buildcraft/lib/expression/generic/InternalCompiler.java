package buildcraft.lib.expression.generic;

import java.util.*;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

import buildcraft.lib.expression.generic.Arguments.ArgType;
import buildcraft.lib.expression.generic.Arguments.ArgumentCounts;

class InternalCompiler {
    private static String operators = "+-*/^%~";
    private static String splitters = operators + "(),";
    private static String leftAssosiative = "+-^*/%";
    private static String rightAssosiative = "-~";
    private static String[] precedence = { "()", "+-", "%", "*/", "^", "~" };
    /** This is not a complete encompassing regular expression, just for the char set that can be used */
    private static String expressionRegex = "[a-z0-9]|[+\\-*/^%()~,\\._<=>]";
    private static Pattern expressionMatcher = Pattern.compile(expressionRegex);
    private static String longNumberRegex = "[-+]?[0-9]+";
    private static Pattern longNumberMatcher = Pattern.compile(longNumberRegex);
    private static String doubleNumberRegex = "[-+]?[0-9]+(\\.[0-9]+)?";
    private static Pattern doubleNumerMatcher = Pattern.compile(doubleNumberRegex);
    private static String booleanRegex = "true|false";
    private static Pattern booleanMatcher = Pattern.compile(booleanRegex);
    private static String stringRegex = "\"[a-z_]+\"";
    private static Pattern stringMatcher = Pattern.compile(stringRegex);

    public static String validateExpression(String expression) throws InvalidExpressionException {
        expression = expression.replace(" ", "").toLowerCase(Locale.ROOT);
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (!expressionMatcher.matcher(c + "").matches()) {
                throw new InvalidExpressionException("Could not compile " + expression + ", as the " + i + "th char ('" + c + "') was invalid");
            }
        }
        return expression;
    }

    public static String[] split(String function) {
        function = function.replaceAll("\\s", "");
        List<String> list = Lists.newArrayList();
        StringBuffer buffer = new StringBuffer();
        for (int index = 0; index < function.length(); index++) {
            char toTest = function.charAt(index);
            if (splitters.indexOf(toTest) != -1) {
                if (buffer.length() > 0) {
                    list.add(buffer.toString());
                }
                list.add(String.valueOf(toTest));
                buffer = new StringBuffer();
            } else {
                buffer.append(toTest);
            }
        }
        if (buffer.length() > 0) {
            list.add(buffer.toString());
        }
        return list.toArray(new String[list.size()]);
    }

    private static int getPrecendence(String token) {
        int p = 0;
        if (token.startsWith("ƒ")) {
            return 0;
        }
        for (String pre : precedence) {
            if (pre.contains(token)) {
                return p;
            }
            p++;
        }
        return p;
    }

    public static String[] convertToPostfix(String[] infix) throws InvalidExpressionException {
        // Implementation of https://en.wikipedia.org/wiki/Shunting-yard_algorithm
        Deque<String> stack = Queues.newArrayDeque();
        List<String> postfix = Lists.newArrayList();
        int index = 0;
        for (index = 0; index < infix.length; index++) {
            String token = infix[index];
            if (longNumberMatcher.matcher(token).matches()) {
                // Its a number
                postfix.add(token);
            } else if (",".equals(token)) {
                boolean found = false;
                while (!stack.isEmpty()) {
                    String fromStack = stack.pop();
                    if ("(".equals(fromStack) || fromStack.startsWith("ƒ")) {
                        found = true;
                        stack.push(fromStack);
                        break;
                    } else {
                        postfix.add(fromStack);
                    }
                }
                if (!found) {
                    throw new InvalidExpressionException("Did not find an opening parenthesis for the comma!");
                }
            } else if ("(".equals(token)) {
                stack.push(token);
            } else if (")".equals(token)) {
                boolean found = false;
                while (!stack.isEmpty()) {
                    String fromStack = stack.pop();
                    if ("(".equals(fromStack)) {
                        found = true;
                        break;
                    } else if (fromStack.startsWith("ƒ")) {
                        found = true;
                        // Add it back onto the stack to be used later
                        postfix.add(fromStack);
                        break;
                    } else {
                        postfix.add(fromStack);
                    }
                }
                if (!found) {
                    throw new InvalidExpressionException("Too many closing parenthesis!");
                }
            } else if (operators.contains(token)) {
                // Its an operator
                if ("-".equals(token) && (index == 0 || (operators + "(,").contains(infix[index - 1]))) {
                    // Bit ugly, but we use a tilde for negative numbers
                    token = "~";
                }

                String s;
                while ((s = stack.peek()) != null) {
                    int tokenPrec = getPrecendence(token);
                    int stackPrec = getPrecendence(s);
                    boolean shouldContinue = leftAssosiative.contains(token) && tokenPrec <= stackPrec;
                    if (!shouldContinue && rightAssosiative.contains(token)) {
                        if (tokenPrec > stackPrec) shouldContinue = true;
                    }

                    if (shouldContinue) {
                        postfix.add(stack.pop());
                    } else {
                        break;
                    }
                }
                stack.push(token);
            } else if (index + 1 < infix.length && "(".equals(infix[index + 1])) {
                // Its a function (The next token is an open parenthesis)
                // Prefix it with \u0192, and push it to the stack
                stack.push("ƒ" + token);
                // Also ignore the parenthesis (the function is treated as if it was an open parenthesis)
                index++;
            } else {
                // Assume it is a variable, so its treated as if it was a static number
                postfix.add(token);
            }
        }

        while (!stack.isEmpty()) {
            String operator = stack.pop();
            if ("(".equals(operator)) {
                throw new InvalidExpressionException("Too many opening parenthesis!");
            } else if (")".equals(operator)) {
                throw new InvalidExpressionException("Too many closing parenthesis!");
            } else {
                postfix.add(operator);
            }
        }

        return postfix.toArray(new String[postfix.size()]);
    }

    public static IExpressionNode makeExpression(String[] postfix, Map<String, IExpression> functions) throws InvalidExpressionException {
        List<String> variables = new ArrayList<>();
        List<ArgType> variableTypes = new ArrayList<>();
        Deque<IExpressionNode> stack = Queues.newArrayDeque();
        for (String op : postfix) {
            if ("-".equals(op)) pushBinaryOperatorNode(stack, (a, b) -> b - a);
            else if ("+".equals(op)) pushBinaryOperatorNode(stack, (a, b) -> b + a);
            else if ("*".equals(op)) pushBinaryOperatorNode(stack, (a, b) -> b * a);
            else if ("/".equals(op)) pushBinaryOperatorNode(stack, (a, b) -> b / a);
            else if ("%".equals(op)) pushBinaryOperatorNode(stack, (a, b) -> b % a);
            else if ("^".equals(op)) pushBinaryOperatorNode(stack, (a, b) -> (long) Math.pow(b, a));
            else if ("~".equals(op)) pushUnaryOperatorNode(stack, (a) -> -a);
            else if (longNumberMatcher.matcher(op).matches()) {
                stack.push(new ValueNode(Long.parseLong(op)));
            } else if (op.startsWith("ƒ")) {
                // Its a function
                String function = op.substring(1);
                IExpression func = functions.get(function);
                if (func == null) {
                    throw new IllegalArgumentException("Unknown function " + func);
                } else {
                    pushFunctionNode(stack, function, func);
                }
            } else {
                if (!variables.contains(op)) {
                    variables.add(op);
                }
                int index = variables.indexOf(op);
                stack.push(new VariableNode(index));
            }
        }
        if (stack.size() != 1) {
            throw new InvalidExpressionException("Tried to make an expression with too many nodes! (" + stack + ")");
        }
        return stack.pop();
    }

    private static void pushFunctionNode(Deque<IExpressionNode> stack, String function, IExpression func) throws InvalidExpressionException {
        ArgumentCounts counts = func.getCounts();
        int size = counts.order.size();
        if (stack.size() < size) {
            throw new InvalidExpressionException("Could not pop " + size + " values from the stack for the function " + function);
        }

        List<IExpressionNode> args = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            args.add(stack.pop());
        }

        GenericNode___OLD[] nodes = new GenericNode___OLD[wantedNodes];
        for (int i = wantedNodes - 1; i >= 0; i--) {
            nodes[i] = stack.pop();
        }
        stack.push(new FunctionNode(func, nodes));
    }

    private static void pushBinaryOperatorNode(Deque<GenericNode___OLD> stack, LongBinaryOperator op) throws InvalidExpressionException {
        if (stack.size() < 2) throw new InvalidExpressionException("Could not pop 2 values from the stack!");
        GenericNode___OLD a = stack.pop();
        GenericNode___OLD b = stack.pop();
        stack.push(new BinaryExpressionNode(a, b, op));
    }

    private static void pushUnaryOperatorNode(Deque<GenericNode___OLD> stack, LongUnaryOperator op) throws InvalidExpressionException {
        if (stack.size() < 1) throw new InvalidExpressionException("Could not pop a value from the stack!");
        GenericNode___OLD a = stack.pop();
        stack.push(new UnaryExpressionNode(a, op));
    }
}