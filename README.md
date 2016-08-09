# [WIP] Jjamppong

`Jjamppong` is gui logcat written in [Clojure]


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
- [ ] open log
- [ ] filter, save/load
- [ ] source open if unity log
- [ ] event, kernel log
- [ ] excutable
- [ ] bookmark
- [ ] maybe
   - [ ] plugin
   - [ ] diff

[iookill/LogFilter]: https://github.com/iookill/LogFilter
[Clojure]: https://clojure.org/
[JavaFX]: http://docs.oracle.com/javafx/
