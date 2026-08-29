# Jamn - Personal Server App

The JamnJPSApp is a <b>Java-SE based</b>, experimental scaffold for building individual All-in-One apps and tools using an also JSE based http capeable micro Server (ca. 70 KB).

The Java app functions as an extensible micro server. The Web app as the extensible frontend. Packaged in one executable JAR.

"Extensible" means in this context that new functionalities can be implemented without the need to modify or rebuild the application itself.

It can be done by:
- adding java extensions for the server 
- adding javascripts for the server 
- adding or changing js modules in the web app
- and of course, by just building something new ...

The project combines ideas and experiences from the outdated first version of JamanServer with design features inspired by Spring ( see same app as: <a href="https://github.com/iqbserve/SpringJPSApp">SpringJPSApp</a> demo ).

How ever - it remains a playground for experimenting with techniques and designs. But the goals have remained the same - lightweight tools, independence, and compactness.
<br>
### Workbench Web App - <a href="https://wbapp.iqbserve.de" target="_blank">Demo</a> - <a href="https://github.com/iqbserve/JamnJPSApp/tree/master/web-app" target="_blank">Project</a> 

#### Download and test run

Prerequisites are java 25 and maven 3.9 on the path

```code
$ git clone https://github.com/iqbserve/JamnJPSApp.git
$ cd JamnJPSApp
$ mvn install
$ run.cmd

open a browser window and call
localhost:9090
```

#### <a href="https://github.com/iqbserve/JamnJPSApp?tab=License-1-ov-file">Disclaimer</a>