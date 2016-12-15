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
    input "contactsSensors", "capability.contactSensor",
    title: "Contact Sensors to monitor", multiple: true, required: false
  }
  section("If the Contacts are Closed for.. (default 10)") {
		input "contactThreshold", "number", description: "Number of minutes", required: false
	}
  section("..Monitor these motion sensors..") {
		input "motionSensors", "capability.motionSensor",
    title: "Motion Sensors to monitor", multiple: true, required: false
	}
  section("..and reset Occupancy if Motion is Inactive for.. (default 10)") {
		input "motionThreshold", "number", description: "Number of minutes", required: false
	}
  section("Also, Reset Occupancy if any of these presence sensors leave..") {
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
	subscribe(contactsSensors, "contact", contactHandler)
	subscribe(presenceSensors, "presence", presenceHandler)
  allActivityCheck()
}

//EVENT HANDLER METHODS
def motionHandler(evt) {
//log.debug "motionHandler processed $evt.value"
  switch (evt.value) {
    case "active":
      //Placeholder for active condition
    case "inactive":
      //Placeholder for inactive condition
    default:
      //Placeholder for default case
    	break
	}
}
def contactHandler(evt) {
  switch (evt.value) {
    case "open":
      occupancySwitch.on()
      contactSchedule()
    case "closed":
      //Placeholder for closed condition
    default:
    //Placeholder for default case
    break
  }
}

def presenceHandler(evt) {
 	switch (evt.value) {
    case "not present":
      allActivityCheck()
    	occupancySwitch.off()
    case "present":
    //Placeholder for present condition
    default:
    //Placeholder for default case
    break
  }
}

//HELPER METHODS
def contactSchedule() {
  //Schedule next activity check using contact time
  def contactDelay = (contactThreshold != null && contactThreshold != "") ? contactThreshold * 60 : 600
  runIn(contactDelay, allActivityCheck)
}
def motionSchedule() {
  //Schedule next activity check using motion time
  def motionDelay = (motionThreshold != null && motionThreshold != "") ? motionThreshold * 60 : 600
  runIn(motionDelay, allActivityCheck)
}
def allActivityCheck() {
  def result1 = allContactsClosed()
  def result2 = allMotionInactive()
  def result3 = allClosedForTime()
  def result4 = allInactiveForTime()
  log.debug "allContactsClosed()=$result1"
  log.debug "allMotionInactive()=$result2"
  log.debug "allClosedForTime()=$result3"
  log.debug "allInactiveForTime()=$result4"

}


def allContactsClosed() {
  def result = true
  for (sensor in contactsSensors) {
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

def allClosedForTime() {
  if (allContactsClosed()) {
    def longEnough = contactsSensors.findAll { contact ->
      def state = contact.currentState("contact")
      if (!state) {
        // State is unkownn, treat it as not away long enough
        return false
      }
      def elapsed = now() - state.rawDateCreated.time
      def delay = (contactThreshold != null && contactThreshold != "") ? contactThreshold * 60 * 1000 : 60 * 1000
      elapsed >= delay
    }
  }

  def result = longEnough != null ? longEnough.size() == contactsSensors.size() : false
  return result
}

def allInactiveForTime() {
  if (allMotionInactive()) {
    def longEnough = motionSensors.findAll { motion ->
      def state = motion.currentState("motion")
      if (!state) {
        // State is not closed, treat it as not away long enough
      return false
      }
      def elapsed = now() - state.rawDateCreated.time
      def delay = (motionThreshold != null && motionThreshold != "") ? motionThreshold * 60 * 1000 : 10 * 60 * 1000
      elapsed >= delay
    }
  }
  def result = longEnough != null ? longEnough.size() == motionSensors.size() : false
  return result
}
