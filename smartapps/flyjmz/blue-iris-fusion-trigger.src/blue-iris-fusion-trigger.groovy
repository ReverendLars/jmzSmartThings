/**
 *  Blue Iris Fusion - Trigger 
 *  (Child app.  Parent app is: "Blue Iris Fusion") 
 *
 *  Created by FLYJMZ (flyjmz230@gmail.com)
 *
 *  Smartthings Community Thread: https://community.smartthings.com/t/release-blue-iris-fusion-integrate-smartthings-and-blue-iris/54226
 *  Github: https://github.com/flyjmz/jmzSmartThings/tree/master/smartapps/flyjmz/blue-iris-fusion-trigger.src
 *
 *  PARENT APP CAN BE FOUND ON GITHUB: https://github.com/flyjmz/jmzSmartThings/tree/master/smartapps/flyjmz/blue-iris-fusion.src
 *
 *
 *  Based on work by:
 *  Tony Gutierrez in "Blue Iris Profile Integration"
 *  jpark40 at https://community.smartthings.com/t/blue-iris-profile-trigger/17522/76
 *  luma at https://community.smartthings.com/t/blue-iris-camera-trigger-from-smart-things/25147/9
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Version 1.0 - 30July2016 	Initial release
 *	Version 1.1 - 3August2016	Cleaned up code
 * 	Version 1.2 - 4August2016	Added Alarm trigger capability from rayzurbock
 *  Version 2.0 - 14Dec2016     Added ability to restrict triggering to defined time periods
 *
 * TODO: 
 *      -Add notifications on trigger? (it'd remove the need to have another app notifying, but may also just be redundant without making it have full capes...)
 *      -Let it trigger for the other states (switch off, contact closed, etc so it'll work for things like the porch mat)
 */

definition(
    name: "Blue Iris Fusion - Trigger",
    namespace: "flyjmz",
    author: "flyjmz230@gmail.com",
    parent: "flyjmz:Blue Iris Fusion",
    description: "Child app to 'Blue Iris Fusion.' Install that app, it will call this during setup.",
    category: "Safety & Security",
    iconUrl: "https://raw.githubusercontent.com/flyjmz/jmzSmartThings/master/resources/BlueIris_logo.png",
    iconX2Url: "https://raw.githubusercontent.com/flyjmz/jmzSmartThings/master/resources/BlueIris_logo%402x.png")

preferences {
	page(name: "mainPage", title: "", install: true, uninstall: true)
    page(name: "certainTime")
}

def mainPage() {
	return dynamicPage(name: "mainPage", title: "") {
    	section("Blue Iris Camera Name"){
			paragraph "Use the Blue Iris short name for the camera, it is case-sensitive."
            input "biCamera", "text", title: "Camera Name", required: true
		}
		section("Select trigger events"){   
			input "myMotion", "capability.motionSensor", title: "Motion Sensors Active", required: false, multiple: true
			input "myContact", "capability.contactSensor", title: "Contact Sensors Opening", required: false, multiple: true
            input "mySwitch", "capability.switch", title: "Switches Turning On", required: false, multiple: true
            input "myAlarm", "capability.alarm", title: "Alarm Activated", required: false, multiple: true
            paragraph "Note: Only the Active/Open/On events will send a trigger.  Motion stopping, Contacts closing, and Switches turning off will not send a trigger."
		}
        section(title: "More options", hidden: hideOptionsSection(), hideable: true) {
            def timeLabel = timeIntervalLabel()
            href "certainTime", title: "Only during a certain time", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null
            input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false, options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            input "modes", "mode", title: "Only when mode is", multiple: true, required: false
        }
        section("") {
        	input "customTitle", "text", title: "Assign a Name", required: true
        }
	}
}

def certainTime() {
    dynamicPage(name:"certainTime",title: "Only during a certain time", uninstall: false) {
        section() {
            input "startingX", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(startingX in [null, "A specific time"]) input "starting", "time", title: "Start time", required: false
            else {
                if(startingX == "Sunrise") input "startSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(startingX == "Sunset") input "startSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
        
        section() {
            input "endingX", "enum", title: "Ending at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(endingX in [null, "A specific time"]) input "ending", "time", title: "End time", required: false
            else {
                if(endingX == "Sunrise") input "endSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(endingX == "Sunset") input "endSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    subscribeToEvents()
    app.updateLabel("${customTitle}")
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribeToEvents()
    app.updateLabel("${customTitle}")
}

def subscribeToEvents() {
	subscribe(myMotion, "motion.active", eventHandlerBinary)
	subscribe(myContact, "contact.open", eventHandlerBinary)
	subscribe(mySwitch, "switch.on", eventHandlerBinary)
	subscribe(myAlarm, "alarm.strobe", eventHandlerBinary)
	subscribe(myAlarm, "alarm.siren", eventHandlerBinary)
	subscribe(myAlarm, "alarm.both", eventHandlerBinary)
}

def eventHandlerBinary(evt) {
	log.debug "processed event ${evt.name} from device ${evt.displayName} with value ${evt.value} and data ${evt.data}"
    if (allOk) {
        log.debug "event occured within the desired timing conditions, triggering"
        if (parent.localOnly) localTrigger()
        else externalTrigger()
    } else log.debug "event did not occur within the desired timing conditions, not triggering"
}

def localTrigger() {
	log.debug "Running localTrigger"
	def biHost = "${parent.host}:${parent.port}"
	def biRawCommand = "/admin?camera=${biCamera}&trigger&user=${parent.username}&pw=${parent.password}"
    log.debug "sending GET to URL $biHost/$biRawCommand"
    def httpMethod = "GET"
    def httpRequest = [
        method:     httpMethod,
        path:       biRawCommand,
        headers:    [
                    HOST:       biHost,
                    Accept:     "*/*",
                    ]
        ]
    def hubAction = new physicalgraph.device.HubAction(httpRequest)
    sendHubCommand(hubAction)
}

def externalTrigger() {
    log.debug "Running externalTrigger"
    def errorMsg = "Could not trigger Blue Iris"
    try {
        httpPostJson(uri: parent.host + ':' + parent.port, path: '/json',  body: ["cmd":"login"]) { response ->
            log.debug response.data
            log.debug "logging in"
            if (response.data.result == "fail") {
               log.debug "BI_Inside initial call fail, proceeding to login"
               def session = response.data.session
               def hash = parent.username + ":" + response.data.session + ":" + parent.password
               hash = hash.encodeAsMD5()
               httpPostJson(uri: parent.host + ':' + parent.port, path: '/json',  body: ["cmd":"login","session":session,"response":hash]) { response2 ->
                    if (response2.data.result == "success") {
                        log.debug ("BI_Logged In")
                        log.debug response2.data
                        httpPostJson(uri: parent.host + ':' + parent.port, path: '/json',  body: ["cmd":"status","session":session]) { response3 ->
                            log.debug ("BI_Retrieved Status")
                            log.debug response3.data
                            if (response3.data.result == "success"){
                                    httpPostJson(uri: parent.host + ':' + parent.port, path: '/json',  body: ["cmd":"trigger","camera":biCamera,"session":session]) { response4 ->
                                        log.debug response4.data
                                        log.debug "camera triggerd"
                                        if (response4.data.result == "success") {
                                            httpPostJson(uri: parent.host + ':' + parent.port, path: '/json',  body: ["cmd":"logout","session":session]) { response5 ->
                                                log.debug response5.data
                                                log.debug "Logged out"
                                            }
                                        } else {
                                            log.debug "BI_FAILURE, not triggered"
                                            log.debug(response4.data.data.reason)
                                            sendNotificationEvent(errorMsg)
                                        }
                                    }
                            } else {
                                log.debug "BI_FAILURE, didn't receive status"
                                log.debug(response3.data.data.reason)
                                sendNotificationEvent(errorMsg)
                            }
                        }
                    } else {
                        log.debug "BI_FAILURE, didn't log in"
                        log.debug(response2.data.data.reason)
                        sendNotificationEvent(errorMsg)
                    }
                }
            } else {
                log.debug "FAILURE"
                log.debug(response.data.data.reason)
                sendNotificationEvent(errorMsg)
            }
        }
    } catch(Exception e) {
        log.debug(e)
        sendNotificationEvent(errorMsg)
    }
}

private getAllOk() {
    modeOk && daysOk && timeOk
}

private getDaysOk() {
    def result = true
    if (days) {
        def df = new java.text.SimpleDateFormat("EEEE")
        if (location.timeZone) {
            df.setTimeZone(location.timeZone)
        }
        else {
            df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
        }
        def day = df.format(new Date())
        result = days.contains(day)
    }
    log.trace "daysOk = $result"
    return result
}

private getTimeOk() {
    def result = true
    if ((starting && ending) ||
    (starting && endingX in ["Sunrise", "Sunset"]) ||
    (startingX in ["Sunrise", "Sunset"] && ending) ||
    (startingX in ["Sunrise", "Sunset"] && endingX in ["Sunrise", "Sunset"])) {
        def currTime = now()
        def start = null
        def stop = null
        def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: startSunriseOffset, sunsetOffset: startSunsetOffset)
        if(startingX == "Sunrise") start = s.sunrise.time
        else if(startingX == "Sunset") start = s.sunset.time
        else if(starting) start = timeToday(starting,location.timeZone).time
        s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: endSunriseOffset, sunsetOffset: endSunsetOffset)
        if(endingX == "Sunrise") stop = s.sunrise.time
        else if(endingX == "Sunset") stop = s.sunset.time
        else if(ending) stop = timeToday(ending,location.timeZone).time
        result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
    }
    log.trace "timeOk = $result"
    return result
}

private getModeOk() {
    def result = !modes || modes.contains(location.mode)
    return result
}

private hhmm(time, fmt = "h:mm a") {
    def t = timeToday(time, location.timeZone)
    def f = new java.text.SimpleDateFormat(fmt)
    f.setTimeZone(location.timeZone ?: timeZone(time))
    f.format(t)
}

private hideOptionsSection() {
    (starting || ending || days || modes || startingX || endingX) ? false : true
}

private offset(value) {
    def result = value ? ((value > 0 ? "+" : "") + value + " min") : ""
}

private timeIntervalLabel() {
    def result = ""
    if (startingX == "Sunrise" && endingX == "Sunrise") result = "Sunrise" + offset(startSunriseOffset) + " to " + "Sunrise" + offset(endSunriseOffset)
    else if (startingX == "Sunrise" && endingX == "Sunset") result = "Sunrise" + offset(startSunriseOffset) + " to " + "Sunset" + offset(endSunsetOffset)
    else if (startingX == "Sunset" && endingX == "Sunrise") result = "Sunset" + offset(startSunsetOffset) + " to " + "Sunrise" + offset(endSunriseOffset)
    else if (startingX == "Sunset" && endingX == "Sunset") result = "Sunset" + offset(startSunsetOffset) + " to " + "Sunset" + offset(endSunsetOffset)
    else if (startingX == "Sunrise" && ending) result = "Sunrise" + offset(startSunriseOffset) + " to " + hhmm(ending, "h:mm a z")
    else if (startingX == "Sunset" && ending) result = "Sunset" + offset(startSunsetOffset) + " to " + hhmm(ending, "h:mm a z")
    else if (starting && endingX == "Sunrise") result = hhmm(starting) + " to " + "Sunrise" + offset(endSunriseOffset)
    else if (starting && endingX == "Sunset") result = hhmm(starting) + " to " + "Sunset" + offset(endSunsetOffset)
    else if (starting && ending) result = hhmm(starting) + " to " + hhmm(ending, "h:mm a z")
}