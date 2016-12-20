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
    description: "Detect Occupancy in your home using activity from switches and motion.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Occupancy is detected when any of these sensors open..") {
    input "contactSensors", "capability.contactSensor",
    title: "Contact Sensors to monitor", multiple: true, required: false
  }
  section("If the Contacts are Closed for..(1-1440)") {
		input "contactDelay", "number",
    title: "Number of minutes", range: "1..1440", defaultValue: "10", required: false
	}
  section("..Monitor these motion sensors..") {
		input "motionSensors", "capability.motionSensor",
    title: "Motion Sensors to monitor", multiple: true, required: false
    input "motionTrigger", "bool", title: "Occupancy on motion?"
	}
  section("..and reset Occupancy if Motion is Inactive for..(1-1440)") {
		input "motionDelay", "number",
    title: "Number of minutes", range: "1..1440", defaultValue: "10", required: false
	}
  section("Also, check contacts and motion if any of these people leave..") {
    input "presenceSensors", "capability.presenceSensor",
    title: "Select presence sensor", multiple: true, required: false
  }
  section("Control this switch..") {
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
	subscribe(motionSensors, "motion", motionHandler)
	subscribe(contactSensors, "contact", contactHandler)
	subscribe(presenceSensors, "presence", presenceHandler)
    activityCheckSchedule(contactDelay)
    activityCheckSchedule(motionDelay)
    allActivityCheck()
}

//EVENT HANDLER METHODS
def motionHandler(evt) {
//log.debug "motionHandler processed $evt.value"
  switch (evt.value) {
    case "active":
      if (motionTrigger) {
        occupancySwitch.on()
        log.debug "turned on Occupancy because $evt.name detected motion"
        sendNotificationEvent ("Occupancy turned On because $evt.name detected motion")
      }
      break
    case "inactive":
      if (allMotionInactive()) {
        activityCheckSchedule(motionDelay)
      }
      break
    default:
      log.debug "motionHandler received unknown event: $evt.value"
	}
}
def contactHandler(evt) {
  switch (evt.value) {
    case "open":
      occupancySwitch.on()
      sendNotificationEvent ("Occupancy turned On because $evt.name opened")
      break
    case "closed":
      if (allContactsClosed()) {
          activityCheckSchedule(contactDelay)
      }
      break
    default:
      log.debug "contactHandler received unknown event: $evt.value"
  }
}

def presenceHandler(evt) {
 	switch (evt.value) {
    case "not present":
      if (allContactsClosed() && allMotionInactive()){
        occupancySwitch.off()
        log.debug "Turned off Occupancy Switch because someone left.."
        sendNotificationEvent ("Turned off Occupancy Switch because $evt.name left, doors were closed, and motion was inactive.")
      }
      break
    case "present":
    //Placeholder for present condition
      break
    default:
      log.debug "presenceHandler received unknown event: $evt.value"
  }
}

//HELPER METHODS
def activityCheckSchedule(delayMinutes) {
  //Schedule next activity check using contact time
  def delaySeconds = delayMinutes ? delayMinutes * 60 : 600
  runIn(delaySeconds, allActivityCheck, [overwrite: false])
}
def allActivityCheck() {
  if(allContactsClosed() && allMotionInactive()) {
    if(allStateForTime(contactSensors, "contact", contactDelay) && 
       allStateForTime(motionSensors, "motion", motionDelay)) {
      occupancySwitch.off()
    }
  }
}

def allContactsClosed() {
  def result = true
  for (sensor in contactSensors) {
    if (sensor.currentContact != "closed") {
      result = false
    }
  }
  log.debug "allContactsClosed: $result"
  return result
}
def allMotionInactive() {
  def result = true
  for (sensor in motionSensors) {
    if (sensor.currentMotion != "inactive") {
      result = false
    }
  }
  log.debug "allMotionInactive: $result"
  return result
}

def allStateForTime(sensors, stateName, delayMinutes) {
  def delayMilliseconds = delayMinutes ? timeOffset(delayMinutes) : timeOffset(10)
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
  log.debug "pastThreshold = ${pastThreshold?.size()}, sensors = ${sensors?.size()}"
  log.debug "allStateForTime($sensors, $stateName, $delayMinutes) = ${result}"
  return result
}

