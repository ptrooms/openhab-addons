## openHAB 2 Add-ons

Note: local ../bundles/ are for desktop/local use and not part of this compiled repo

This repository contains add-ons that are implemented using the new [Eclipse SmartHome APIs](https://www.eclipse.org/smarthome/documentation/development/bindings/how-to.html) of openHAB 2.

Note that all information about openHAB itself, the IDE setup and the contribution processes can be found in the [openhab-distro](https://github.com/openhab/openhab-distro) project, so please go there for any further details!

file ../addons/binding/org.openhab.binding.avmfritz/src/main/java/org/openhab/binding/avmfritz/internal/hardware/FritzAhaWebInterface_r3.java
is to compare release 3 against release 2. Release3 is extended to support newer functionality.


## Add-ons in other repositories
Some add-ons (e.g. specific bindings such as [Z-Wave](https://github.com/openhab/org.openhab.binding.zwave)) are maintained in separate repositories in order to improve their management. In order to contribute to these bindings, you should follow the following steps -:

1. Fork the repository on Github
2. Clone your repository to your local computer as described in the [Github tutorial](https://help.github.com/articles/cloning-a-repository/)
3. Open the openHAB Eclipse IDE
4. Select the *File | Import* menu option
5. Select *General | Existing Projects into Workspace* and click Next
6. Select the root directory where you made the local clone of the repository
7. Select the project and click *Next*
8. The project will now be imported and available in the Package Explorer
9. You may want to add the project to the *OH2 Add-ons* Working Set
