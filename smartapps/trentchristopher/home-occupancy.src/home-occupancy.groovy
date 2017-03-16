/**
 *  Home Occupancy
 *
 *  Copyright 2016 Trent Christopher
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
 */
definition(
    name: "Home Occupancy",
    namespace: "trentchristopher",
    author: "Trent Christopher",
    description: "Detect Occupancy in your home using activity from switches, motion, and presence.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
  section("Occupancy is true when these sensors arrive.."){
    input "arrivingSensors", "capability.presenceSensor",
    title: "Select presence sensor(s)", multiple: true, required: false
  }
  section("Also, true if any of these sensors opened..") {
    input "contactSensors", "capability.contactSensor",
    title: "Contact Sensors to monitor", multiple: true, required: false
  }
  section("Monitor these motion sensors..") {
	input "motionSensors", "capability.motionSensor",
    title: "Motion Sensors to monitor", multiple: true, required: false
    input "motionTrigger", "bool", title: "Occupancy on motion?"
	}
  section("No Occupancy if there has been no arrivals in the last(1..240 hours).."){
    input "arrivalHours", "number",
    title: "Number of hours", range: "1..240", defaultValue: "12", required: false
  }
  section("..and Contacts have been Closed for(1-1440 minutes)..") {
	input "contactMinutes", "number",
    title: "Number of minutes", range: "1..1440", defaultValue: "10", required: false
	}
  section("..and Motion is Inactive for(1-1440 minutes)") {
	input "motionMinutes", "number",
    title: "Number of minutes", range: "1..1440", defaultValue: "10", required: false
	}
  section("Also, check contacts and motion if any of these sensors leave..") {
    input "departingSensors", "capability.presenceSensor",
    title: "Select presence sensor(s)", multiple: true, required: false
  }
  section("Control this switch for occupancy") {
    input "occupancySwitch", "capability.switch",
    title: "Select Switch", multiple: false
  }
}
//Standard Smartapp Install, update, subscribe
def installed() {
	subs()
}

def updated() {
	unsubscribe()
	subs()
}

def subs() {
  subscribe(arrivingSensors, "presence", arrivalHandler)
  subscribe(contactSensors, "contact", contactHandler)
  subscribe(motionSensors, "motion", motionHandler)
  subscribe(departingSensors, "presence", departingHandler)
  allActivityCheck()
}

//EVENT HANDLER METHODS
def arrivalHandler(evt) {
 	switch (evt.value) {
    case "not present":
      allActivityCheck()
      break
    case "present":
      if (occupancySwitch.currentValue("switch") != "on") {
        occupancySwitch.on()
        sendNotificationEvent ("Occupancy turned On because $evt.displayName arrived")
        }
      break
    default:
      log.debug "arrivalHandler received unknown event: $evt.value"
  }
}
def contactHandler(evt) {
  switch (evt.value) {
    case "open":
      if (occupancySwitch.currentValue("switch") != "on") {
        occupancySwitch.on()
        sendNotificationEvent ("Occupancy turned On because $evt.displayName opened")
      }
      break
    case "closed":
      scheduleNext()
      break
    default:
      log.debug "contactHandler received unknown event: $evt.value"
  }
}
def motionHandler(evt) {
//log.debug "motionHandler processed $evt.value"
  switch (evt.value) {
    case "active":
      if (motionTrigger) {
        if (occupancySwitch.currentValue("switch") != "on") {
          occupancySwitch.on()
          log.debug "turned on Occupancy because $evt.name detected motion"
          sendNotificationEvent ("Occupancy turned On because $evt.name detected motion")
        }
      }
      break
    case "inactive":
      scheduleNext()
      break
    default:
      log.debug "motionHandler received unknown event: $evt.value"
	}
}
def departingHandler(evt) {
 	switch (evt.value) {
    case "not present":
      if (allContactsClosed() && allMotionInactive()){
        if (occupancySwitch.currentValue("switch") != "off") {
          occupancySwitch.off()
          log.debug "Turned off Occupancy Switch because someone left.."
          sendNotificationEvent ("Turned off Occupancy Switch because $evt.displayName left, doors were closed, and motion was inactive.")
        }
      }
      break
    case "present":
    //Placeholder for present condition
    //   break
    default:
      log.debug "departingHandler received unknown event: $evt.value"
  }
}

// Main Activity Handler
def allActivityCheck() {
  log.debug "<<<<<<<<<< ACTIVITY CHECK >>>>>>>>>>"
  def allClosed = allContactsClosed() && allStateForTime(contactSensors, "contact", contactMinutes)
  def allInactive = allMotionInactive() && allStateForTime(motionSensors, "motion", motionMinutes)
  def noPresence = allNotPresent()
  if(allClosed && allInactive && noPresence) {
    if (occupancySwitch.currentValue("switch") != "off") {
      occupancySwitch.off()
      sendNotificationEvent ("Home Occupancy: Turned off Occupancy Switch because after delay time, doors were closed, and motion was inactive.")
    }
  } else {
    sendNotificationEvent ("Home Occupancy: All Closed? $allClosed, All Motion Inactive? $allInactive, No Presence? $noPresence")
  }
  scheduleNext() 
}
// STATE HELPERS
def allContactsClosed() {
  def result = true
  for (sensor in contactSensors) {
    if (sensor.currentValue("contact") != "closed") {
      result = false
    }
  }
  log.debug "allContactsClosed: $result"
  return result
}
def allMotionInactive() {
  def result = true
  for (sensor in motionSensors) {
    if (sensor.currentValue("motion") != "inactive") {
      result = false
    }
  }
  log.debug "allMotionInactive: $result"
  return result
}
def allNotPresent() {
  def result = true
  def delayMilliseconds = arrivalHours ? timeOffset(arrivalHours*60) : timeOffset(60)
  def threshold = now() - delayMilliseconds
  for (sensor in arrivingSensors) {
    def state = sensor.currentState("presence")
//  log.debug "state= ${state}, value= ${sensor.currentValue("presence")}"
// 	log.debug "threshold(${threshold}) < state.rawDateCreated.time(${state.rawDateCreated.time}) = ${threshold < state.rawDateCreated.time}"
    //If present, check if event is recent
	if (sensor.currentValue("presence") == "present") {
    	if (threshold < state.rawDateCreated.time) {
    		log.debug "allNotPresent returned false because ${sensor} state is recent"
      		result = false
    	} else {log.debug "${sensor} state is stale"}
  	}
  }
  log.debug "allNotPresent: $result"
  return result
}

def allStateForTime(sensors, stateName, minutes) {
  def delayMilliseconds = minutes ? timeOffset(minutes) : timeOffset(10)
  def threshold = now() - delayMilliseconds
  def pastThreshold = sensors.findAll {sensor ->
    def state = sensor.currentState("${stateName}")
    if (!state) {
      return false // State is unknown, treat it as not away long enough
    }
/*  Logging for debug...
    log.debug "sensor = ${sensor}, state = ${state}"
    log.debug "state time= ${state.rawDateCreated.time}, threshold=${threshold}"
    log.debug "result= ${state.rawDateCreated.time < threshold}"
*/
    state.rawDateCreated.time < threshold
    }
  def result =  pastThreshold?.size() == sensors?.size()
//  log.debug "pastThreshold = ${pastThreshold?.size()}, sensors = ${sensors?.size()}"
  log.debug "allStateForTime($sensors, $stateName, $minutes) = ${result}"
  return result
}
// SCHEDULING HELPERS
def scheduleNext() {
  def delaySeconds = (nextThreshold() / 1000)
  log.debug "scheduleNext scheduled next check in $delaySeconds seconds"
  runIn(delaySeconds, allActivityCheck, [overwrite: true])
}

def nextThreshold() {
  def eventTimes = []
  arrivingSensors.each { sensor ->
    def state = sensor.currentState("presence")
    if (state) {
      def nextTime = state.rawDateCreated.time + timeOffset(arrivalHours * 60)
      if (nextTime > now()) {
        eventTimes << nextTime - now()
      }
    }
  }
  contactSensors.each { sensor ->
    def state = sensor.currentState("contact")
    if (state) {
      def nextTime = state.rawDateCreated.time + timeOffset(contactMinutes)
      if (nextTime > now()) {
        eventTimes << nextTime - now()
      }
    }
  }
  motionSensors.each { sensor ->
    def state = sensor.currentState("motion")
    if (state) {
      def nextTime = state.rawDateCreated.time + timeOffset(motionMinutes)
      if (nextTime > now()) {
        eventTimes << nextTime - now()
      }
    }
  }
  log.debug "eventTimes = $eventTimes, min= ${eventTimes.min()}"
  eventTimes.min()
}

