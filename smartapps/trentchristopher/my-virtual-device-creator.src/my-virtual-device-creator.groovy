/**
 *  My Virtual Switch (on/off or momentary) Creator
 *
 *  Copyright 2015 Anthony Pastor
 *  Based in LARGE part on "Virtual On/Off Switch Creator" by Eric Roberts
 *
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
    name: "My Virtual Device Creator",
    namespace: "trentchristopher",
    author: "Trent Christopher",
    description: "App that can create virtual devices!",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Create a Virtual Device") {
		input "devlabel", "text", title: "Label", required: true, submitOnChange: true
        input "switchType", "enum", title: "What kind of Virtual Device do you want?", multiple: false, required: true, metadata: [
          values: [
            "On/Off Button Tile", 
            "Momentary Button Tile", 
            "Virtual Dimmer", 
            "Virtual Presence Switch"
          ]
        ], defaultValue: "On/Off Button Tile"
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}
def uninstalled() {
    removeChildDevices(getAllChildDevices())
}
def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
  def deviceId = app.id + "VirtualDevice"
  log.debug(deviceId)
  def existing = getChildDevice(deviceId)

	if (!existing) {
    	if (switchType == "On/Off Button Tile") {
	    	def childDevice = addChildDevice("smartthings", "On/Off Button Tile", deviceId, null, [label: devlabel])           
	    } else if (switchType == "Momentary Button Tile") {
			def childDevice = addChildDevice("smartthings", "Momentary Button Tile", deviceId, null, [label: devlabel])    
	    } else if (switchType == "Dimmer Switch") {
			def childDevice = addChildDevice("smartthings", "Virtual Dimmer", deviceId, null, [label: devlabel])    
	    } else if (switchType == "Virtual Presence Switch") {
			def childDevice = addChildDevice("trentchristopher", "Virtual Presence Switch", deviceId, null, [label: devlabel])    
		}
	}
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}