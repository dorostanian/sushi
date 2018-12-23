export interface CodeEditorTab {
  title: string;
  text: string;
}

const mainFlow =
`[[action]]
name = "intial action"
source = true
type = "http-get"
id = "initial-action"
next = ["new-defined-action"]
[action.params]
url = "https://postman-echo.com/get?foo1=bar1&foo2=bar2"
  
[[action]]
name = "new defined action"
type = "constant"
id = "new-defined-action"
next = ["4"]
[action.params]
value = "branch-2"

[[branch]]
name = "branch-1"
id = "4"
on = "value"
[branch.mapping]
branch-1 = "5"
branch-2 = "6"
branch-3 = "7"

[[action]]
name = "action-3"
type = "constant"
id = "5"
next = ["7"]
[action.params]
param-4 = "value-4"

[[action]]
name = "Branch eventually came here!"
type = "constant"
id = "6"
returnAfter = false
next = ["new-action-id"]
[action.params]
value = "value-5"

[[action]]
name = "action-5"
type = "constant"
id = "7"
output-names = ["something-to-pass"] # if you dont specify this field it will pass all the data it has.
[action.params]
param-6 = "value-6"
  
[[action]]
name = "Use New Registered Action Here"
type = "new-action"
id = "new-action-id"
`;

const containers =
`[[container]]
name = "My Container"
id = "my-container"
type = "new-action"
description = "This new action does a simple task"
first = "cont-1"
last = "cont-3"

[[action]]
name = "Inside container 1"
type = "log"
id = "cont-1"
next = ["cont-2"]

[[action]]
name = "Inside container 2"
type = "delay"
id = "cont-2"
next = ["cont-3"]
[action.params]
seconds = "3"

[[action]]
name = "Inside container 3"
type = "log"
id = "cont-3"
`;

export {mainFlow, containers};
