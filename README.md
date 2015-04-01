October 24, 2014   Steve Podell

Uses the Jue Jenkins library, which I compiled and then Mac compressed into a zip file, then renamed it to be a jar, which I called hueLib-1.0.jar.  
I then checked this jar into my local maven repository.

https://github.com/Q42/Jue

This app is checked into my open to the public githup account "SailingSteve"

The app relies on a config.properties file 

Steve-Podells-MacBook-Pro-17:creds stevepodell$ cat config.properties 
job1=PSA - develop - Deploy to QA -- DevOrg31
job2=PSA - develop - Unit Tests
job3=PSA-MobleTimecard-develop-DevOrg94
urlString=https://ci.dev.financialforce.com/view/PSA/api/json
username=spodell@financialforce.com
password=e1eb6eb5566fe317391
hueBridgeIP=10.0.1.40
jenkins_blue_hue=46920
jenkins_red_hue=65280
jenkins_blue_anime_hue=46920
jenkins_red_anime_hue=65280
jenkins_disabled_color_temperature=500
jenkins_blue_brightness=41
jenkins_red_brightness=42
jenkins_blue_anime_brightness=42
jenkins_red_anime_brightness=42
jenkins_disabled_brightness=42
jenkins_blue_anime_alert=false
jenkins_red_anime_alert=false
polling_delay_ms=15000
Steve-Podells-MacBook-Pro-17:creds stevepodell$ 

That password is my Jenkins "API Token"

If you have a Jenkins login you should be able to see the api response in your browser

https://ci.dev.financialforce.com/view/PSA/api/json

How the api works:
	https://wiki.jenkins-ci.org/display/JENKINS/Remote+access+API

How to get your API token:
https://wiki.jenkins-ci.org/display/JENKINS/Authenticating+scripted+clients
