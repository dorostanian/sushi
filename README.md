# Introduction

**Sushi** is a simplistic flow design based on `TOML` config files offering also an UI (WIP) to make it easy to build
complex flows of actions. Flow is highly extensible by allowing you to `register` new `Block`s. There comes also a library
of commonly used actions.

## Similar Work and comparison
Tinder's [State Machine Project](https://github.com/Tinder/StateMachine) is one of the interesting
implementations for building state machines. (tbd)



# Motivation

The idea of building **Sushi** born in my mind while trying to build a software that contains lots of steps and conditions.
In the beginning you start writing the definition of states and their conections but eventually it grows to an spaghetti,
to overcome this issue I started The **Sushi** Project. Sushi helps us to write complicated flows in easy and expressive way.


# How it works

Sushi is built on top of concept of `Block`s. Every unit is a block and we have three different kinds of blocks which enables
you to build almost anything with a very explicit and simple data presentation.

* `Action` blocks: These are the smallest units of actions that will be executed in the flow.
* `Branch` blocks: You need them to branch your flow based on certain conditions. It makes it easy to do control your flow with them.
* `Container` blocks: To make complex blocks and reusing them you can use containers. They will be like holders of multiple blocks
making it easy to present your flow.

To define your flows, there are two ways. You can define your flows in multiple files inside TOML files, and import them.
The other ways is to use the UI to generate these TOML files for you.

## `Action` block
This block is the most essential unit in Sushi. These blocks are supposed to fulfill a small unit of work and then
point to another block. The field `type` specifies what this block is supposed to do.

```toml
[[action]]
    name = "action-1"
    type = "log"
    id = "2"
    next = ["3"]
    output-names = ["some-variable-name"]
    [action.params]
     param-1 = "value-2"
```
For all the blocks we define `id`s must be unique to that block. `params` are basically parameters specific to the 
mentioned type of action, there might be different parameters for one single action.

Every action block looks like this:

![Action Block](docs/action.png)

Normally `input` is the previous block's `output` and `params` are configurations applied to make actions flexible and
configurable.

if you need the flow engine to return the output of current action without continuing execution of 
rest of the flow automatically you can put `returnAfter = true`. This tells the engine to hold the
output of this block after execution and `id` of it to keep tracks of the flow. **This is mostly
useful when you are expecting a user interaction e.g. Rest API.**

You can register listeners for actions to have a callback mechanism after executing each action.
This helps communicate easily while the engine takes care of running the actions.


**You need to make sure that you have at least one block that has `source = true`. This tells the engine where it 
needs to start the execution. You can have multiple source blocks.**

> If you are writing your flows programmatically it is also possible to inject any object you want to your defined actions.


## `Branch` block
This block is designed intentionally simple to make it easy for controling the flow of your work.
```toml
[[branch]]
    name = "branch-1"
    id = "4"
    on = "value"
    [branch.mapping]
        branch-1 = "5"
        branch-2 = "6"
        branch-3 = "7"
```
Any branch block should contain `var-name` param that tells the engine to pick the data with this key from input
and compare it with `mapping` values. For instance in this example if `input["value"]==branch-2` then next block
will be the block with id of `6`. So `mapping` is like a switch statement over `value`.

**NOTE:**

If the branch type is `ROUTER` then this branch will route the flow to the id specified in `value` field directly.
This can be very useful for jumping to different blocks based on different sources. 

## `Container` block
And the last block which enables re-using sub-flows that are already defined. `container`s hold multiple blocks and
make it easy to build hierarchical flows. It will define you a new type of `action` which you can re-use anywhere you want.

Container Definition:

```toml
[[container]]
    name = "container-block"
    type = "new-defined-action"
    id = "1"
    first = "2"
    last = "3"
```

Container Usage:

```toml
[[action]]
    name = "Using a defined container!"
    type = "new-defined-action"
    id = "some-id"
```


The most important fields of this block are `first` and `last` which tells the id of the blocks that should be executed
first and last respectively.

**NOTE:**
You are not allowed to use `returnAfter=true` inside a container.


## Programmatical Definition (DSL)
(tbd)

# Usage

You can use **Sushi** in two different ways: as a library to import in your project or launch
it as service and get the benefits of graphical UI designer.

There are three ways of building flows.
1. Reading `TOML` files from a directory.
2. Building the flows programmatically. 
3. Using the UI.



```kotlin
val flowEngine = FlowEngine()
val flows = flowEngine.readFlowsFromDir("flows/")

flowEngine.wire(flows)
flowEngine.executeFlow()
```


## Define Flows Programmatically
```kotlin
val flows = mutableListOf(
    Action().apply {
        name = "input 1"
        id = "1"
        type = "constant"
        source = true
        params = mutableMapOf("value" to "5")
        nextBlocks = mutableListOf("delay-1")
    }, Action().apply {
        name = "input 2"
        id = "2"
        source = true
        type = "constant"
        params = mutableMapOf("value" to "3")
        nextBlocks = mutableListOf("delay-2")
    }, Action().apply {
        name = "Delay 1"
        id = "delay-1"
        params = mutableMapOf("seconds" to "2")
        type = "delay"
        nextBlocks = mutableListOf("3")
    }, Action().apply {
        name = "Delay 2"
        params = mutableMapOf("seconds" to "2")
        id = "delay-2"
        type = "delay"
        nextBlocks = mutableListOf("3")
    }, Action().apply {
        name = "Log the result"
        id = "3"
        type = "log"
    }
)

flowEngine.wire(flows)
flowEngine.executeFlow()
flowEngine.await()

```


* You can also register custom action types. Keep in mind that types must be unique for actions.

* You can build any action based on the elementary actions (no need to further implementation),
 but if you think you need more actions; you can implement and register your own types. 

