/*
 * Copyright 2014 IBM Corp. All Rights Reserved
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.bluelist;

import com.ibm.mobile.services.data.IBMDataObject;
import com.ibm.mobile.services.data.IBMDataObjectSpecialization;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@IBMDataObjectSpecialization("Item")
public class Item extends IBMDataObject {
	public static final String CLASS_NAME = "Item";
	private static final String NAME = "name";
	private Date createdDate = new Date();


	public Set<String> getTags() {
		return tags;
	}

	public void setTags(Set<String> tags) {
		this.tags = tags;
	}

	private Set<String> tags = new HashSet<String>();

	
	/**
	 * Gets the name of the Item.
	 * @return String itemName
	 */
	public String getName() {
	return (String) getObject(NAME);
	/* String ret =  getName();
	for (String tag : tags){
		ret += ' ' + tag.toString();
	}
		return ret; */
}

	/**
	 * Sets the name of a list item, as well as calls setCreationTime().
	 * @param String itemName
	 */
	public void setName(String itemName) {
		setObject(NAME, (itemName != null) ? itemName : "");
	}
	
	/**
	 * When calling toString() for an item, we'd really only want the name.
	 * @return String theItemName
	 */
	public String toString() {
		String ret = getName();
		for (String tag : tags){
			ret += " " + tag.toString();
		}

		return ret;
	}

	public String toDetailedString() {
		String ret = toString();
		return ret;
		//TODO: to return full text
	}

	public boolean match(String searchConditions){
		for(String tag : tags){
			if (tag.toLowerCase().contains(searchConditions.toLowerCase()))
					return true;
		}
		return false;
	}

	public void addTag(String tag){
		tags.add(tag);
	}




}
