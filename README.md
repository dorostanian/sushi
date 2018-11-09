# Introduction

**Flow** is a simplistic flow design based on `TOML` config files offering also an UI (WIP) to make it easy to build
complex flows of actions. Flow is highly extensible by allowing you to `register` new `Block`s. There comes also a library
of commonly used actions.

# Motivation

The idea of building **Flow** born in my mind while trying to build a software that contains lots of steps and conditions.



# Usage

Flow is built on top of concept of `Block`s. Every unit is a block and we have three different kinds of blocks which enables
you to build almost anything with a very explicit and simple data presentation.

* `Action` blocks: These are the smallest units of actions that will be executed in the flow.
* `Branch` blocks: You need them to branch your flow based on certain conditions. It makes it easy to do control your flow with them.
* `Container` blocks: To make complex blocks and reusing them you can use containers. They will be like holders of multiple blocks
making it easy to present your flow.

To define your flows, there are two ways. You can define your flows in multiple files inside TOML files, and import them.
The other ways is to use the UI to generate these TOML files for you.

## Definition of a Block

(WIP).



You can also register custom action types. Keep in mind that types must be unique.