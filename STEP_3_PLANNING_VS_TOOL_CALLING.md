# Step 3: Planning vs Tool Calling - The Key Difference

## The Core Difference

### Tool Calling = "What do I need RIGHT NOW?"
The LLM makes an **immediate, single-turn decision** about which tool(s) to use.

### Planning = "What STEPS do I need to complete this task?"
The LLM creates a **multi-step plan** where later steps can depend on earlier results.

---

## Visual Comparison

### Tool Calling (Current Implementation)

```
User: "Calculate 25 * 40"
     ↓
┌────────────────────────────────┐
│ LLM Analyzes Request           │
│ Decision: "Need calculator"    │
└────────────────────────────────┘
     ↓
┌────────────────────────────────┐
│ Execute: calculator("25*40")   │
│ Result: 1000                   │
└────────────────────────────────┘
     ↓
┌────────────────────────────────┐
│ LLM: "The result is 1000"      │
└────────────────────────────────┘

Turns: 2 (request → tool → response)
Steps: 1
Dependencies: None
```

### Planning (What We'll Add)

```
User: "Calculate 25 * 40 and tell me the current time in Bangalore"
     ↓
┌────────────────────────────────────────────────────────┐
│ Planner (LLM) Analyzes Request                         │
│ Creates Plan:                                          │
│   Step 1: calculator("25 * 40")                        │
│   Step 2: get_current_time("Asia/Kolkata")            │
└────────────────────────────────────────────────────────┘
     ↓
┌────────────────────────────────────────────────────────┐
│ PlanExecutor:                                          │
│                                                        │
│ Execute Step 1: calculator("25*40")                    │
│   Result: {"result": 1000}                            │
│                                                        │
│ Execute Step 2: get_current_time("Asia/Kolkata")      │
│   Result: {"time": "14:30:00", ...}                   │
└────────────────────────────────────────────────────────┘
     ↓
┌────────────────────────────────────────────────────────┐
│ LLM: "25 * 40 = 1000. The time in Bangalore is 2:30 PM"│
└────────────────────────────────────────────────────────┘

Turns: 2 (request → plan+execute → response)
Steps: 2
Dependencies: None (independent steps)
```

### Planning with Dependencies (Advanced)

```
User: "Get temperature in Bangalore and convert it to Fahrenheit"
     ↓
┌────────────────────────────────────────────────────────┐
│ Planner (LLM) Analyzes Request                         │
│ Creates Plan:                                          │
│   Step 1: get_weather(location="Bangalore")            │
│            → variable: "celsius_temp"                  │
│   Step 2: calculator("celsius_temp * 9/5 + 32")        │
│            → depends_on: Step 1                        │
└────────────────────────────────────────────────────────┘
     ↓
┌────────────────────────────────────────────────────────┐
│ PlanExecutor:                                          │
│                                                        │
│ Execute Step 1: get_weather("Bangalore")               │
│   Result: {"temperature": 28, "unit": "C"}            │
│   Store: celsius_temp = 28                            │
│                                                        │
│ Execute Step 2: calculator("28 * 9/5 + 32")           │
│   (Substituted celsius_temp)                          │
│   Result: {"result": 82.4}                            │
└────────────────────────────────────────────────────────┘
     ↓
┌────────────────────────────────────────────────────────┐
│ LLM: "Temperature in Bangalore is 28°C (82.4°F)"      │
└────────────────────────────────────────────────────────┘

Turns: 2 (request → plan+execute → response)
Steps: 2
Dependencies: Step 2 depends on Step 1's result
```

---

## Key Differences Table

| Aspect | Tool Calling | Planning |
|--------|-------------|----------|
| **Decision Type** | Immediate | Multi-step |
| **Execution Model** | Parallel tools | Sequential steps |
| **Dependencies** | None | Steps can depend on previous results |
| **Complexity** | Simple, direct queries | Complex, multi-part tasks |
| **LLM Usage** | Once (decide tools) | Twice (plan, then final response) |
| **Example** | "What time is it?" | "Get weather and convert to Fahrenheit" |

---

## When to Use Each

### Use Tool Calling (Current System) For:

✅ **Immediate, single-action requests**
- "What time is it?"
- "Calculate 25 * 40"
- "How many conversations do I have?"

✅ **Independent parallel operations**
- "Calculate 10 + 5 and tell me the time"
  (Two independent tools, no dependency)

✅ **Simple lookups**
- "What's my conversation count?"

### Use Planning (New System) For:

✅ **Sequential operations with dependencies**
- "Get temperature in Bangalore and convert to Fahrenheit"
  (Step 2 needs Step 1's result)

✅ **Multi-step calculations**
- "Calculate 25 * 40, then add 100 to the result"
  (Step 2 depends on Step 1)

✅ **Complex tasks requiring breakdown**
- "Find all conversations from last week and calculate average message count"
  (Step 1: find conversations, Step 2: calculate average)

---

## How Planning Uses the SAME Tools

**Critical Design Decision:** Planning does NOT create new tools. It REUSES existing tools.

```
Current Tool Registry:
- calculator
- get_current_time
- get_conversation_stats

Planning adds:
- Planner (generates plans)
- PlanExecutor (executes plans using existing tools)

Planning does NOT add:
- New tools (uses existing ones)
- Duplicate execution logic (uses ToolExecutor)
```

---

## Planning Architecture Components

### 1. Plan (Data Structure)

```java
public class Plan {
    private String id;                  // Unique plan ID
    private String description;         // What this plan does
    private List<PlanStep> steps;       // Ordered list of steps
    
    // Methods
    public void addStep(PlanStep step)
    public PlanStep getStep(int index)
    public boolean isComplete()
}
```

### 2. PlanStep (Data Structure)

```java
public class PlanStep {
    private int stepNumber;             // 1, 2, 3, ...
    private String toolName;            // "calculator", "get_current_time", etc.
    private String arguments;           // JSON arguments for the tool
    private String outputVariable;      // Where to store result (optional)
    private List<Integer> dependsOn;    // Which steps must complete first (optional)
    private String result;              // Execution result (null until executed)
    private boolean executed;           // Has this step run?
    
    // Methods
    public boolean canExecute(Plan plan)  // Are dependencies satisfied?
    public void execute(ToolExecutor executor)
}
```

### 3. Planner (LLM-based Plan Generator)

```java
@Component
public class Planner {
    @Autowired
    private OpenAIClient openAIClient;
    
    @Autowired
    private ToolRegistry toolRegistry;
    
    /**
     * Analyzes user request and generates a structured plan.
     * Uses LLM with structured output (JSON) to ensure parseable plans.
     */
    public Plan createPlan(String userRequest, List<Message> history) {
        // 1. Build prompt for LLM
        String prompt = buildPlanningPrompt(userRequest, toolRegistry.getAllDefinitions());
        
        // 2. Call OpenAI with structured output request
        // Ask LLM to return JSON: { steps: [{toolName, arguments, dependsOn}] }
        String planJson = openAIClient.sendMessage(...);
        
        // 3. Parse JSON into Plan object
        Plan plan = parsePlanJson(planJson);
        
        // 4. Validate: all tools exist in registry
        validatePlan(plan);
        
        return plan;
    }
    
    /**
     * Decides if a request needs planning or can use simple tool calling.
     */
    public boolean needsPlanning(String userRequest) {
        // Simple heuristic for now:
        // - Contains "then", "and then", "after that" → needs planning
        // - Contains "first", "second", "finally" → needs planning
        // - Otherwise → simple tool calling
        
        // Future: use LLM to decide
    }
}
```

### 4. PlanExecutor (Executes Plans)

```java
@Component
public class PlanExecutor {
    @Autowired
    private ToolExecutor toolExecutor;  // REUSE existing executor
    
    /**
     * Executes a plan step-by-step, handling dependencies.
     */
    public Map<String, Object> executePlan(Plan plan) {
        Map<String, Object> results = new HashMap<>();
        
        for (PlanStep step : plan.getSteps()) {
            // 1. Check dependencies are satisfied
            if (!step.canExecute(plan)) {
                throw new PlanExecutionException("Step " + step.getStepNumber() + 
                    " dependencies not satisfied");
            }
            
            // 2. Substitute variables in arguments
            String resolvedArgs = substituteVariables(step.getArguments(), results);
            
            // 3. Execute via existing ToolExecutor (REUSE!)
            String result = toolExecutor.executeTool(step.getToolName(), resolvedArgs);
            
            // 4. Store result if variable name provided
            if (step.getOutputVariable() != null) {
                results.put(step.getOutputVariable(), parseResult(result));
            }
            
            // 5. Mark step as executed
            step.setResult(result);
            step.setExecuted(true);
        }
        
        return results;
    }
    
    private String substituteVariables(String args, Map<String, Object> results) {
        // Replace placeholders like {{variable_name}} with actual values
        // Example: "{{celsius_temp}} * 9/5 + 32" → "28 * 9/5 + 32"
    }
}
```

---

## Planning Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│ User: "Calculate 25*40 and tell me time in Bangalore"   │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│ ConversationService.sendMessageStream()                 │
│                                                         │
│ Decision Point: Does this need planning?                │
│   - Check for keywords: "then", "after", etc.           │
│   - Check for sequential logic                          │
│                                                         │
│ Result: YES (has "and" with multiple operations)        │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│ Planner.createPlan(userRequest)                         │
│                                                         │
│ LLM Prompt:                                             │
│ "Given these tools: [calculator, get_current_time, ...]│
│  Create a plan for: 'Calculate 25*40 and tell time'    │
│  Return JSON: {steps: [{toolName, arguments}]}"        │
│                                                         │
│ LLM Returns:                                            │
│ {                                                       │
│   "steps": [                                            │
│     {                                                   │
│       "stepNumber": 1,                                  │
│       "toolName": "calculator",                         │
│       "arguments": "{\"expression\": \"25*40\"}"        │
│     },                                                  │
│     {                                                   │
│       "stepNumber": 2,                                  │
│       "toolName": "get_current_time",                   │
│       "arguments": "{\"timezone\": \"Asia/Kolkata\"}"   │
│     }                                                   │
│   ]                                                     │
│ }                                                       │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│ PlanExecutor.executePlan(plan)                          │
│                                                         │
│ Step 1:                                                 │
│   toolExecutor.executeTool("calculator", "25*40")       │
│   Result: {"result": 1000}                              │
│                                                         │
│ Step 2:                                                 │
│   toolExecutor.executeTool("get_current_time", ...)    │
│   Result: {"time": "14:30:00", ...}                     │
│                                                         │
│ All steps complete ✓                                    │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│ OpenAI Final Response Generation                        │
│                                                         │
│ Messages:                                               │
│   - user: "Calculate 25*40 and tell time"               │
│   - assistant: "I executed a plan with results..."      │
│   - system: "Generate final response"                   │
│                                                         │
│ LLM Returns:                                            │
│ "25 * 40 = 1000. The time in Bangalore is 2:30 PM."    │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│ Stream to Frontend (SAME as current tool calling)       │
│   - event: chunk                                        │
│   - event: chunk                                        │
│   - event: done                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Planning with Dependencies Example

```
User: "Get temperature in Bangalore and convert to Fahrenheit"

Plan Generated:
{
  "steps": [
    {
      "stepNumber": 1,
      "toolName": "get_weather",
      "arguments": "{\"location\": \"Bangalore\"}",
      "outputVariable": "celsius_temp"
    },
    {
      "stepNumber": 2,
      "toolName": "calculator",
      "arguments": "{\"expression\": \"{{celsius_temp}} * 9/5 + 32\"}",
      "dependsOn": [1]
    }
  ]
}

Execution:
Step 1:
  toolExecutor.executeTool("get_weather", "{\"location\": \"Bangalore\"}")
  Result: {"temperature": 28, "unit": "C"}
  Store: celsius_temp = 28

Step 2:
  Substitute: "{{celsius_temp}} * 9/5 + 32" → "28 * 9/5 + 32"
  toolExecutor.executeTool("calculator", "{\"expression\": \"28 * 9/5 + 32\"}")
  Result: {"result": 82.4}

Final Response:
  "The temperature in Bangalore is 28°C, which is 82.4°F."
```

---

## What Planning Does NOT Do (Yet)

❌ **Loops** - "Keep trying until success" requires agent loop  
❌ **Conditionals** - "If X then Y else Z" requires agent reasoning  
❌ **Self-correction** - "Try, observe, retry" requires agent loop  
❌ **Learning** - "Remember this for next time" requires memory system  

**Planning is LINEAR:**
- Step 1 → Step 2 → Step 3 → Done

**Agent Loop is ITERATIVE:**
- Step 1 → Observe → Step 2 → Observe → Step 3 → Observe → Done
- (Can branch, loop, retry based on observations)

---

## Why This Design?

### 1. Reuses Existing Infrastructure
- ✅ Uses ToolRegistry (no duplicate tool management)
- ✅ Uses ToolExecutor (no duplicate execution logic)
- ✅ Uses OpenAIClient (no new API client)
- ✅ Uses same tools (calculator, time, stats)

### 2. Preserves Streaming
- ✅ Final response still streams to frontend
- ✅ SSE events unchanged
- ✅ User experience identical

### 3. Security Maintained
- ✅ Planner can ONLY use registered tools
- ✅ No arbitrary code execution
- ✅ Same whitelist approach

### 4. Minimal Changes
- ✅ No changes to ToolRegistry, ToolExecutor, tools
- ✅ No changes to ConversationController
- ✅ Minor changes to ConversationService (add planning decision)

### 5. Incremental Enhancement
- ✅ Simple queries still use direct tool calling (fast)
- ✅ Complex queries use planning (powerful)
- ✅ No regression in existing functionality

---

## Summary: Planning Layer

**What is Planning?**
- Multi-step task decomposition
- LLM generates structured plan (JSON)
- Steps execute sequentially via existing ToolExecutor
- Later steps can use earlier results

**What Planning is NOT:**
- Not a replacement for tool calling (it's an addition)
- Not an agent loop (no iteration/observation)
- Not new tools (uses existing ones)

**Key Benefit:**
Handles complex, multi-step requests that current tool calling cannot.

**Key Constraint:**
Must reuse existing tools and infrastructure. No duplication.

---

## Next: Step 4

Propose minimal architecture for implementing the planning layer.
