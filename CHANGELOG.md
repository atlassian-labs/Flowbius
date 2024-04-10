Change Log
==========
## Version 0.1.2
_2024-04-10

* Fixes an issue in `FlowConnectable` that could emit data before the subscribing flow is complete, potentially dropping data.

## Version 0.1.1

_2022-10-27_

* Fixed an issue where we could drop inital events: https://github.com/atlassian-labs/Flowbius/pull/7
* Fixed an issue where we didn't declare the correct compatability: https://github.com/atlassian-labs/Flowbius/pull/9

## Version 0.1.0

_2022-02-07_

* Initial release
