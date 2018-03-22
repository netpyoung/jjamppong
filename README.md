# [WIP] Jjamppong

`Jjamppong` is gui logcat written in [Clojure]
* this is toy project for experience of gui and playground of clojure's techiniq.

* this project's name is temporary. so, this repository url will be changed (maybe)

* coding coding until tired.
* thinking thinking until enlightenment.

[![jjamppong](https://img.youtube.com/vi/X9a6c1IQp0o/0.jpg)](https://www.youtube.com/watch?v=X9a6c1IQp0o)


## Play
* http://boot-clj.com/

``` clojure
# dev
> boot dev

# production
> boot build
> java -jar target/project.jar
```


## history
When i was testing android app via `adb logcat`, It's not enough for me.
 so I'm had using [iookill/LogFilter].
 Someday, I want to writting gui logcat like [iookill/LogFilter], using in my interesting [Clojure] and [JavaFX].


## stack
* https://github.com/boot-clj/boot
  - build-tool
* https://github.com/danielsz/system
  - Reloaded
* https://github.com/rufoa/named-re
  - Named capturing group support for clojure regular expressions




## TODO
- [ ] make define.clj
- [ ] optimize scroll view. why too slow.
  - [ ] check https://github.com/FXMisc/Flowless
- [ ] adb path config
- [ ] device list
  - [ ] left side show/hide
- [ ] support adb shell monkey -v 100
- [ ] filter - window
    - [ ] message contain/remove (optional regex)
    - [ ] tag, pid, tid
    - [ ] easy add/remove/enable/disable
- [ ] install/remove app
- [ ] highlight
- [ ] error hightligh - https://github.com/frosch95/SmartCSV.fx/blob/master/src/main/java/ninja/javafx/smartcsv/fx/list/ErrorSideBar.java
- [ ] open log async
- [ ] data view
    - [ ] xml, json
    - [ ] url encode/decode
- [ ] rest test
- [ ] pipeline
- [ ] config save/load - right click support
- [ ] source open if unity log
- [ ] event, kernel log
- [ ] excutable
- [ ] bookmark - save/load
- [ ] goto line
- [ ] notify
- [ ] maybe
   - [ ] plugin
   - [ ] diff
   - [ ] dark theme
- [ ] refinement pattern - philosophy.
- [ ] external styling - https://github.com/wkf/hawk

``` clojure
File f = new File("filecss.css");
scene.getStylesheets().clear();
scene.getStylesheets().add("file:///" + f.getAbsolutePath().replace("\\", "/"));
```


## styling
* http://fxexperience.com/controlsfx/
* https://github.com/jfoenixadmin/JFoenix
* https://github.com/RobertBColton/DockFX - lgpl3
[iookill/LogFilter]: https://github.com/iookill/LogFilter
[Clojure]: https://clojure.org/
[JavaFX]: http://docs.oracle.com/javafx/
