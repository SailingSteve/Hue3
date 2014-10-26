#!/bin/bash
cd /Users/stevepodell/workspace/hue3
java -cp "creds:target/hue3-1.0-SNAPSHOT.jar:$(echo lib/*.jar | tr ' ' ':')" main.java.com.podell.HueController 
