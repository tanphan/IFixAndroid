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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.ibm.mobile.services.cloudcode.IBMCloudCode;
import com.ibm.mobile.services.core.http.IBMHttpResponse;
import com.ibm.mobile.services.data.IBMDataException;
import com.ibm.mobile.services.data.IBMDataObject;
import com.ibm.mobile.services.data.IBMQuery;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import bolts.Continuation;
import bolts.Task;


public class MainActivity extends Activity {

    List<Item> itemList;
    BlueListApplication blApplication;
    ArrayAdapter<Item> lvArrayAdapter;
    ActionMode mActionMode = null;
    int listItemPosition;
    public static final String CLASS_NAME = "MainActivity";
    private boolean updateMode = false;

    static Set<Item> data = new HashSet<Item>();

    @Override
    /**
     * onCreate called when main activity is created.
     *
     * Sets up the itemList, application, and sets listeners.
     *
     * @param savedInstanceState
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		/* Use application class to maintain global state. */
        blApplication = (BlueListApplication) getApplication();
        itemList = blApplication.getItemList();
		
		/* Set up the array adapter for items list view. */
        ListView itemsLV = (ListView) findViewById(R.id.itemsList);
        lvArrayAdapter = new ArrayAdapter<Item>(this, R.layout.list_item_1, itemList);
        itemsLV.setAdapter(lvArrayAdapter);
		
		/* Refresh the list. */
        listItems();

		/* Set long click listener. */
        itemsLV.setOnItemLongClickListener(new OnItemLongClickListener() {
            /* Called when the user long clicks on the textview in the list. */
            public boolean onItemLongClick(AdapterView<?> adapter, View view, int position,
                                           long rowId) {
                listItemPosition = position;
                if (mActionMode != null) {
                    return false;
                }
		        /* Start the contextual action bar using the ActionMode.Callback. */
                mActionMode = MainActivity.this.startActionMode(mActionModeCallback);
                return true;
            }
        });
        EditText itemToAdd = (EditText) findViewById(R.id.itemToAdd);
		/* Set key listener for edittext (done key to accept item to list). */
        itemToAdd.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    createItem(v);
                    return true;
                }
                return false;
            }
        });
        initData();
    }

    /**
     * Removes text on click of x button.
     *
     * @param v the edittext view.
     */
    public void clearText(View v) {
        EditText itemToAdd = (EditText) findViewById(R.id.itemToAdd);
        itemToAdd.setText("");
    }

    /**
     * Refreshes itemList from data service.
     * <p/>
     * An IBMQuery is used to find all the list items.
     */
    public void listItems() {
        try {
            IBMQuery<Item> query = IBMQuery.queryForClass(Item.class);
            /**
             * IBMQueryResult is used to receive array of objects from server.
             *
             * onResult is called when it successfully retrieves the objects associated with the
             * query, and will reorder these items based on creation time.
             *
             * onError is called when an error occurs during the query.
             */
            query.find().continueWith(new Continuation<List<Item>, Void>() {

                @Override
                public Void then(Task<List<Item>> task) throws Exception {
                    // Log error message, if the save task is cancelled.
                    if (task.isCancelled()) {
                        Log.e(CLASS_NAME, "Exception : Task " + task.toString() + " was cancelled.");
                    }
                    // Log error message, if the save task fails.
                    if (task.isFaulted()) {
                        Log.e(CLASS_NAME, "Exception : " + task.getError().getMessage());
                    }

                    // If the result succeeds, load the list.
                    else {
                        final List<Item> objects = task.getResult();
                        // Clear local itemList, as we'll be reordering & repopulating from DataService.
                        itemList.clear();
                        for (IBMDataObject item : objects) {
                            itemList.add((Item) item);
                        }
                        sortItems(itemList);
                        lvArrayAdapter.notifyDataSetChanged();
                    }
                    return null;
                }
            }, Task.UI_THREAD_EXECUTOR);

        } catch (IBMDataException error) {
            Log.e(CLASS_NAME, "Exception : " + error.getMessage());
        }
    }

    /**
     * Send a notification to all devices whenever the BlueList is modified (create, update, or delete).
     */
    private void updateOtherDevices() {

        // Initialize and retrieve an instance of the IBM CloudCode service.
        IBMCloudCode.initializeService();
        IBMCloudCode myCloudCodeService = IBMCloudCode.getService();
        JSONObject jsonObj = new JSONObject();
        try {
            jsonObj.put("key1", "value1");
        } catch (JSONException e) {
            e.printStackTrace();
        }

		/*
		 * Call the node.js application hosted in the IBM Cloud Code service
		 * with a POST call, passing in a non-essential JSONObject.
		 * The URI is relative to/appended to the BlueMix context root.
		 */

        myCloudCodeService.post("notifyOtherDevices", jsonObj).continueWith(new Continuation<IBMHttpResponse, Void>() {

            @Override
            public Void then(Task<IBMHttpResponse> task) throws Exception {
                if (task.isCancelled()) {
                    Log.e(CLASS_NAME, "Exception : Task" + task.isCancelled() + "was cancelled.");
                } else if (task.isFaulted()) {
                    Log.e(CLASS_NAME, "Exception : " + task.getError().getMessage());
                } else {
                    InputStream is = task.getResult().getInputStream();
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(is));
                        String responseString = "";
                        String myString = "";
                        while ((myString = in.readLine()) != null)
                            responseString += myString;

                        in.close();
                        Log.i(CLASS_NAME, "Response Body: " + responseString);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    Log.i(CLASS_NAME, "Response Status from notifyOtherDevices: " + task.getResult().getHttpResponseCode());
                }

                return null;
            }

        });

    }

    /**
     * On return from other activity, check result code to determine behavior.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
		/* If an edit has been made, notify that the data set has changed. */
            case BlueListApplication.EDIT_ACTIVITY_RC:
                updateOtherDevices();
                sortItems(itemList);
                lvArrayAdapter.notifyDataSetChanged();
                break;
        }
    }

    /**
     * Called on done and will add item to list.
     *
     * @param v edittext View to get item from.
     * @throws IBMDataException
     */
    public void createItem(View v) {
        EditText itemToAdd = (EditText) findViewById(R.id.itemToAdd);
        String toAdd = itemToAdd.getText().toString();
        Item item = new Item();
        if (!toAdd.equals("")) {
            item.setName(toAdd);
            /**
             * IBMObjectResult is used to handle the response from the server after
             * either creating or saving an object.
             *
             * onResult is called if the object was successfully saved.
             * onError is called if an error occurred saving the object.
             */
            item.save().continueWith(new Continuation<IBMDataObject, Void>() {

                @Override
                public Void then(Task<IBMDataObject> task) throws Exception {
                    // Log error message, if the save task is cancelled.
                    if (task.isCancelled()) {
                        Log.e(CLASS_NAME, "Exception : Task " + task.toString() + " was cancelled.");
                    }
                    // Log error message, if the save task fails.
                    else if (task.isFaulted()) {
                        Log.e(CLASS_NAME, "Exception : " + task.getError().getMessage());
                    }

                    // If the result succeeds, load the list.
                    else {
                        listItems();
                        updateOtherDevices();
                    }
                    return null;
                }

            });

            // Set text field back to empty after item added.
            itemToAdd.setText("");
        }
    }

    /**
     * Will delete an item from the list.
     *
     * @param Item item to be deleted.
     */
    public void deleteItem(Item item) {
        itemList.remove(listItemPosition);
        // This will attempt to delete the item on the server.
        item.delete().continueWith(new Continuation<IBMDataObject, Void>() {

            // Called if the object was successfully deleted.
            @Override
            public Void then(Task<IBMDataObject> task) throws Exception {
                // Log error message, if the delete task is cancelled.
                if (task.isCancelled()) {
                    Log.e(CLASS_NAME, "Exception : Task " + task.toString() + " was cancelled.");
                }
                // Log error message, if the delete task fail.
                else if (task.isFaulted()) {
                    Log.e(CLASS_NAME, "Exception : " + task.getError().getMessage());
                }

                // If the result succeeds, reload the list.
                else {
                    updateOtherDevices();
                    lvArrayAdapter.notifyDataSetChanged();
                }
                return null;
            }
        }, Task.UI_THREAD_EXECUTOR);

        lvArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Will call new activity for editing item on list.
     *
     * @param String name - name of the item.
     */
    public void updateItem(String name) {
        Intent editIntent = new Intent(getBaseContext(), EditActivity.class);
        editIntent.putExtra("ItemText", name);
        editIntent.putExtra("ItemLocation", listItemPosition);
        startActivityForResult(editIntent, BlueListApplication.EDIT_ACTIVITY_RC);
    }

    /**
     * Sort a list of Items.
     *
     * @param List<Item> theList
     *                   TODO: sort by created date? -- TP
     */
    private void sortItems(List<Item> theList) {
        // Sort collection by case insensitive alphabetical order.
        Collections.sort(theList, new Comparator<Item>() {
            public int compare(Item lhs,
                               Item rhs) {
                String lhsName = lhs.getName();
                String rhsName = rhs.getName();
                return lhsName.compareToIgnoreCase(rhsName);
            }
        });
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	        /* Inflate a menu resource with context menu items. */
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.editaction, menu);
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * Called when user clicks on contextual action bar menu item.
         *
         * Determined which item was clicked, and then determine behavior appropriately.
         *
         * @param  item menu item clicked
         */
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Item lItem = itemList.get(listItemPosition);
	    	/* Switch dependent on which action item was clicked. */
            switch (item.getItemId()) {
	    		/* On edit, get all info needed & send to new, edit activity. */
                case R.id.action_edit:
                    updateItem(lItem.getName());
                    mode.finish(); /* Action picked, so close the CAB. */
                    return true;
	            /* On delete, remove list item & update. */
                case R.id.action_delete:
                    deleteItem(lItem);
                    mode.finish(); /* Action picked, so close the CAB. */
                default:
                    return false;
            }
        }

        /* Called on exit of action mode. */
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    };

    public void onUpdateToggleClicked(View view) {


        updateMode = ((Switch)view).isChecked();
        TextView titleText = (TextView) findViewById(R.id.titleText);
        EditText editText = (EditText) findViewById(R.id.itemToAdd);
        editText.setText("");
        if(updateMode){
            titleText.setText("Resolver notes");
            editText.setHint("Add inc:env:rootcause:resolution:resolver details");
        }
        else{
            titleText.setText("Look up");
            editText.setHint("Lookup items e.g. 'EVTE SRP server'");
        }
    }

    /**
     * Called when user clicks on contextual action bar menu item.
     *
     * Determined which item was clicked, and then determine behavior appropriately.
     *
     * @param  item menu item clicked
     * TP: mocked search response
     */
    public void lookupItem(View v) {
        final List<Item> objects = new ArrayList<Item>();
        itemList.clear();
        EditText searchString = (EditText) findViewById(R.id.itemToAdd);
        itemList.addAll(search(searchString.getText().toString().trim()));
        //sortItems(itemList);
        lvArrayAdapter.notifyDataSetChanged();
    }

    public void onEditTextFieldUpdate(View view) {
        if (updateMode) {
            createItem(view);
        } else {
            lookupItem(view);
        }
    }
    /**
     *
     * TP: TODO: init from local datasource
     */
    public void initData(){
        Item item1 = new Item();
        item1.addTag("EVTE MEIG Node 1");
        item1.setName("EVTE MEIG Node 1: H211WVA1");
        data.add(item1);
        Item item2 = new Item();
        item2.addTag("EVTE MEIG Node 2");
        item2.setName("EVTE MEIG Node 2: H211WVA2");
        data.add(item2);
    }

    public List<Item> search(String searchString){
        List<Item> matchedItems = new ArrayList<Item>();
        for(Item item : data){
            if (item.match(searchString))
                matchedItems.add(item);
        }
        return matchedItems;
    }

   /* @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData, boolean globalSearch) {
        ()
    }*/
}
