package com.abhay.tool.impl;

import com.abhay.model.llm.ToolDefinition;
import com.abhay.tool.Tool;
import com.abhay.tool.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculator Tool
 * Performs safe mathematical calculations. Supports: +, -, *, /, %, parentheses, and decimal numbers.
 * SECURITY: Uses manual expression parser (no eval/ScriptEngine). Only mathematical operations are allowed - no code execution.
 * Example usage: User: "What is 25 * 40?" LLM calls: calculator({ "expression": "25 * 40" }) Tool returns: { "result": 1000 } LLM responds:
 * "25 * 40 equals 1000"
 */
@Component
public class CalculatorTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Performs mathematical calculations. Supports +, -, *, /, %, parentheses, and decimal numbers. "
                + "Example: '25 * 40' returns 1000.";
    }

    @Override
    public ToolDefinition getDefinition() {
        // JSON Schema for the parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> expressionProperty = new HashMap<>();
        expressionProperty.put("type", "string");
        expressionProperty.put("description", "The mathematical expression to evaluate (e.g., '25 * 40', '(10 + 5) * 2')");
        properties.put("expression", expressionProperty);

        parameters.put("properties", properties);
        parameters.put("required", new String[] { "expression" });

        return ToolDefinition.create(getName(), getDescription(), parameters);
    }

    @Override
    public String execute(String arguments) throws ToolExecutionException {
        try {
            // Parse JSON arguments
            JsonNode argsNode = objectMapper.readTree(arguments);
            String expression = argsNode.get("expression").asText();

            // Validate expression (basic safety check)
            if (expression == null || expression.trim().isEmpty()) {
                throw new ToolExecutionException("Expression cannot be empty");
            }

            // Evaluate expression safely
            double result = evaluateExpression(expression.trim());

            // Return result as JSON
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("expression", expression);

            return objectMapper.writeValueAsString(response);

        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to evaluate expression: " + e.getMessage(), e);
        }
    }

    /**
     * Safe expression evaluator
     * Simple recursive descent parser for mathematical expressions. Supports: +, -, *, /, %, parentheses, decimal numbers.
     * Grammar: expression = term (('+' | '-') term)* term = factor (('*' | '/' | '%') factor)* factor = number | '(' expression ')'
     */
    private double evaluateExpression(String expression) throws ToolExecutionException {
        // Remove all whitespace
        expression = expression.replaceAll("\\s+", "");

        // Validate characters (only digits, operators, parentheses, decimal point)
        if (!expression.matches("[0-9+\\-*/%().]+")) {
            throw new ToolExecutionException(
                    "Invalid characters in expression. Only numbers and operators (+, -, *, /, %, parentheses) are allowed.");
        }

        try {
            Parser parser = new Parser(expression);
            double result = parser.parseExpression();

            if (parser.hasMoreTokens()) {
                throw new ToolExecutionException("Unexpected characters after expression");
            }

            return result;
        } catch (Exception e) {
            throw new ToolExecutionException("Invalid expression: " + e.getMessage());
        }
    }

    /**
     * Recursive descent parser for mathematical expressions
     */
    private static class Parser {

        private final String expression;
        private int position = 0;

        public Parser(String expression) {
            this.expression = expression;
        }

        public boolean hasMoreTokens() {
            return position < expression.length();
        }

        private char peek() {
            if (position >= expression.length()) {
                return '\0';
            }
            return expression.charAt(position);
        }

        private char consume() {
            return expression.charAt(position++);
        }

        // expression = term (('+' | '-') term)*
        public double parseExpression() {
            double result = parseTerm();

            while (hasMoreTokens()) {
                char op = peek();
                if (op == '+' || op == '-') {
                    consume();
                    double right = parseTerm();
                    result = (op == '+') ? result + right : result - right;
                } else {
                    break;
                }
            }

            return result;
        }

        // term = factor (('*' | '/' | '%') factor)*
        private double parseTerm() {
            double result = parseFactor();

            while (hasMoreTokens()) {
                char op = peek();
                if (op == '*' || op == '/' || op == '%') {
                    consume();
                    double right = parseFactor();
                    if (op == '*') {
                        result = result * right;
                    } else if (op == '/') {
                        if (right == 0) {
                            throw new IllegalArgumentException("Division by zero");
                        }
                        result = result / right;
                    } else {
                        result = result % right;
                    }
                } else {
                    break;
                }
            }

            return result;
        }

        // factor = number | '(' expression ')' | '-' factor
        private double parseFactor() {
            char ch = peek();

            // Unary minus
            if (ch == '-') {
                consume();
                return -parseFactor();
            }

            // Parentheses
            if (ch == '(') {
                consume();
                double result = parseExpression();
                if (peek() != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                consume();
                return result;
            }

            // Number
            return parseNumber();
        }

        private double parseNumber() {
            int start = position;

            while (hasMoreTokens() && (Character.isDigit(peek()) || peek() == '.')) {
                consume();
            }

            if (start == position) {
                throw new IllegalArgumentException("Expected number at position " + position);
            }

            String numberStr = expression.substring(start, position);
            try {
                return Double.parseDouble(numberStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + numberStr);
            }
        }
    }
}
