/*
 * Copyright (c) 2017  STMicroelectronics – All rights reserved
 * The STMicroelectronics corporate logo is a trademark of STMicroelectronics
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions
 *   and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice, this list of
 *   conditions and the following disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name nor trademarks of STMicroelectronics International N.V. nor any other
 *   STMicroelectronics company nor the names of its contributors may be used to endorse or
 *   promote products derived from this software without specific prior written permission.
 *
 * - All of the icons, pictures, logos and other images that are provided with the source code
 *   in a directory whose title begins with st_images may only be used for internal purposes and
 *   shall not be redistributed to any third party or modified in any way.
 *
 * - Any redistributions in binary form shall not include the capability to display any of the
 *   icons, pictures, logos and other images that are provided with the source code in a directory
 *   whose title begins with st_images.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */

package com.st.BlueMS.demos.Cloud;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.st.BlueMS.R;
import com.st.BlueSTSDK.Debug;
import com.st.BlueSTSDK.Utils.ConsoleCommand;
import com.st.BlueSTSDK.gui.fwUpgrade.download.DownloadFwFileService;
import com.st.BlueSTSDK.gui.fwUpgrade.download.DownloadFwFileCompletedReceiver;
import com.st.blesensor.cloud.AwsIot.AwSIotConfigurationFactory;
import com.st.blesensor.cloud.AzureIot.AzureIotConfigFactory;
import com.st.blesensor.cloud.CloudIotClientConfigurationFactory;
import com.st.blesensor.cloud.CloudIotClientConnectionFactory;
import com.st.blesensor.cloud.GenericMqtt.GenericMqttConfigurationFactory;
import com.st.blesensor.cloud.IBMWatson.IBMWatsonQuickStartConfigFactory;
import com.st.blesensor.cloud.util.CloudFwUpgradeRequestDialog;
import com.st.blesensor.cloud.util.MqttClientConfigAdapter;
import com.st.BlueMS.demos.util.DemoWithNetFragment;
import com.st.BlueMS.demos.util.FeatureListViewAdapter;
import com.st.BlueMS.demos.util.FeatureListViewAdapter.OnFeatureSelectChange;
import com.st.BlueSTSDK.Feature;
import com.st.BlueSTSDK.Node;
import com.st.BlueSTSDK.gui.demos.DemoDescriptionAnnotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sent the feature data to a cloud provider
 */
@DemoDescriptionAnnotation(name = "Cloud Logging",
        iconRes = R.drawable.ic_cloud_upload_24dp,
        requareAny=true)
public class CloudLogFragment extends DemoWithNetFragment implements
        CloudIotClientConnectionFactory.FwUpgradeAvailableCallback,
        CloudFwUpgradeRequestDialog.CloudFwUpgradeRequestCallback,
        CloudLogSelectIntervalDialogFragment.CloudLogSelectIntervalDialogCallback{

    private static final String CONF_PREFIX_KEY = CloudLogFragment.class.getCanonicalName();
    private static final String UPDATE_INTERVAL_DIALOG_TAG = CONF_PREFIX_KEY +".UPDATE_INTERVAL_DIALOG_TAG";
    private static final String SELECTED_CLOUD_KEY = CONF_PREFIX_KEY +".SELECTED_CLOUD_KEY";
    private static final String UPDATE_INTERVAL_KEY = CONF_PREFIX_KEY +".UPDATE_INTERVAL_KEY";
    private static final String UPLOAD_BUTTON_VISIBLE_KEY = CONF_PREFIX_KEY +".UPLOAD_BUTTON_VISIBLE_KEY";
    private static final String UPLOAD_BUTTON_NUM_SAMPLES_KEY = CONF_PREFIX_KEY + ".UPLOAD_BUTTON_NUM_SAMPLES_KEY";

    private static final int DEFAULT_UPDATE_INTERVAL_MS = 5000;

    /**
     * List of supported cloud provider
     */
    private List<CloudIotClientConfigurationFactory> mCloudProviders =  Arrays.asList(
            new GenericMqttConfigurationFactory(), // Generic MQTT
            new AzureIotConfigFactory(),      //Generic Azure Dashboard
            new AwSIotConfigurationFactory(), //Generic aws Dashboard
            new IBMWatsonQuickStartConfigFactory() //IBM Quick Start

    );

    /**
     * Node use for the demo
     */
    private Node mNode;

    private static final long COMMAND_TIMEOUT=1000;
    private static final String UID_COMMAND = "uid";
    private static final Pattern BOARD_UID_PARSE = Pattern.compile("([0-9A-Fa-f]*)(_)([0-9A-Fa-f]{3,4})");

    /**
     * STM32 Unique ID
     */
    private static String mMCU_id;

    /**
     * button for start/stop a cloud connection
     */
    private ImageButton mStartLogButton;

    /**
     * Animation for start/stop or upload log button
     */
    private Animation animRotateButton;
    private Animation animRotateBackButton;

    /**
     * button for uploading the data to cloud
     */
    private ExtendedFloatingActionButton mUpoladLogButton;

    private int mNumSamplesForUploadLogButton = 0;

    /**
     * spinner used for select the cloud provider
     */
    private Spinner mCloudClientSpinner;

    /**
     * Button for show/hide the connection parameters
     */
    private Button mShowDetailsButton;

    /**
     * view that contains the feature view list
     */
    private View mFeatureListViewContainer;
    private View mCloudConnectionProgress;
    /**
     * list of avialable feature in the node
     */
    private RecyclerView mFeatureListView;
    /**
     * view where load the configuration gui for the selecte cloud provider
     */
    private FrameLayout mCloudConfig;

    /**
     * object used for build the cloud connection
     */
    private CloudIotClientConnectionFactory mCloudConnectionFactory;

    /**
    * object used to build the current configuration ui
     */
    private CloudIotClientConfigurationFactory mCloudConfigurationFactory;

    /**
     * mqtt connection
     */
    private @Nullable
    CloudIotClientConnectionFactory.CloutIotClient  mMqttClient;

    /**
     * object to use for send the data to the cloud
     */
    private Feature.FeatureListener mCloudLogListener;

    private CloudIotClientConnectionFactory.NewSampleListener mNewSampleListener;


    /**
     * snackbar used for display the page with the data
     */
    private Button mDataPageLink;

    public void UpdateNSamplesExtendedFab(int numSamples) {
        Log.i("UpdateNSamples","numSamples"+numSamples);
        mNumSamplesForUploadLogButton = numSamples;
        updateGui(() ->{
            String text = String.format("Samples %-5d",numSamples);
            mUpoladLogButton.setText(text);
        });
    }

    /**
     * listener that close the cloud connection when the node lost the connection
     */
    private Node.NodeStateListener mCloseConnectionOnDisconnection = new Node.NodeStateListener() {
        @Override
        public void onStateChange(@NonNull Node node, @NonNull Node.State newState, @NonNull Node.State prevState) {
            if (prevState == Node.State.Connected) {
                closeCloudConnection();
                node.removeNodeStateListener(this);
            }
        }
    };


    /*
     * keep track of selected feature to avoid to disable all the notification if the user has not
     * select anything
     */
    private int mNUserSelectedFeature = 0;

    /**
     * Enable/disable the notification for a feature when its state change form the feature list
     */
    private OnFeatureSelectChange mSendDataToCloudWhenSelected = new OnFeatureSelectChange() {
        @Override
        public void onFeatureSelect(Feature f) {
            if (mCloudLogListener != null) {
                f.addFeatureListener(mCloudLogListener);
                f.enableNotification();
                mNUserSelectedFeature++;
            }
        }

        @Override
        public void onFeatureDeSelect(Feature f) {
            if (mCloudLogListener != null) {
                f.removeFeatureListener(mCloudLogListener);
                f.disableNotification();
                mNUserSelectedFeature--;
            }
        }
    };

    /**
     * Receiver called when the system download a new fw, it will start the fw upgrade procedure
     */
    private DownloadFwFileCompletedReceiver mFwDownloadReceiver;

    public CloudLogFragment() {
        // Required empty public constructor
    }

    /**
     * build an dialog for display the error during the connection
     * @param c context to use for build the dialog
     * @param message error to show
     * @return dialog to display with the error message
     */
    private static AlertDialog buildMqttErrorDialog(Context c, String message) {
        return new AlertDialog.Builder(c)
                .setIcon(R.drawable.ic_alert)
                .setTitle(R.string.cloudLog_mqttErrorTitle)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //save the fragment state when the activity is destroyed
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_cloud_log_demo,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.cloudLog_menu_updateInterval) {
            displaySelectUpdateIntervalDialog();
            return true;
        }//else
        return super.onOptionsItemSelected(item);
    }

    private void displaySelectUpdateIntervalDialog() {
        DialogFragment dialog = CloudLogSelectIntervalDialogFragment.create(requireActivity(),
                getUpdateInterval());
        dialog.show(getChildFragmentManager(),UPDATE_INTERVAL_DIALOG_TAG);
    }


    /**
     * filter list using the current selected cloud provider, only the supported features are returned
     * @param availableFeature list of feature exported by the node
     * @return list of feature supported by the selected cloud provider
     */
    private List<Feature> getSupportedFeatures(List<Feature> availableFeature){
        ArrayList<Feature> supportedFeatures = new ArrayList<>(availableFeature.size());
        for(Feature f: availableFeature){
            if(mCloudConnectionFactory.supportFeature(f)){
                supportedFeatures.add(f);
            }
        }

        return supportedFeatures;
    }

    @Override
    protected void enableNeededNotification(@NonNull Node node) {
        Log.i("CloudFragment","enableNeededNotification");
        mNode = node;
        mFwDownloadReceiver = new DownloadFwFileCompletedReceiver(requireContext(),node);

        //exec the command to retrieve the info
        Debug console = node.getDebug();
        if(console!=null) {
            new ConsoleCommand(console, COMMAND_TIMEOUT).exec(UID_COMMAND,
                    new ConsoleCommand.Callback() {
                        @Override
                        public void onCommandResponds(String response) {
                            Matcher matcher = BOARD_UID_PARSE.matcher(response);
                            if (matcher.find()) {
                                mMCU_id = matcher.group(1);
                            } else {
                                mMCU_id = node.getFriendlyName();
                            }
                        }

                        @Override
                        public void onCommandError() {
                            mMCU_id = node.getFriendlyName();
                        }
                    });
        } else {
            mMCU_id =  node.getFriendlyName();
        }

        if (isCloudConnected()) {
            showConnectedView();
        } else
            showDisconnectedView();
    }

    /**
     * utility function that return the selected CloudIotClientConfigurationFactory from the spinner
     * @return current selection form the spinner
     */
    private CloudIotClientConfigurationFactory getSelectCloud() {
        return (CloudIotClientConfigurationFactory) mCloudClientSpinner.getSelectedItem();
    }

    /**
     * this will stop the  notification only the cloud connection is closed, this for permit to
     * send data also if the activity is in background (because we are opening the browser)
     * @param node node where disable the notification
     */
    @Override
    protected void disableNeedNotification(@NonNull Node node) {
        Log.i("CloudFragment","disableNeedNotification");
        if (!isCloudConnected()) {
            stopAllNotification();
        } else {
            Toast.makeText(getActivity(), R.string.cloudLog_warningConnectionOn, Toast.LENGTH_SHORT).show();
            node.addNodeStateListener(mCloseConnectionOnDisconnection);
        }

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i("CloudFragment","onCreateView");
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_cloud_log, container, false);
        mFeatureListView = root.findViewById(R.id.cloudLogFeatureList);
        mFeatureListViewContainer = root.findViewById(R.id.cloudLogFeatureListContainer);

        mStartLogButton = root.findViewById(R.id.startCloudLog);
        setUpStartLogButton(mStartLogButton);

        mUpoladLogButton = root.findViewById(R.id.uploadCloudLog);
        mUpoladLogButton.setOnClickListener(this::onUploadCloudLogClick);
        mUpoladLogButton.hide();
        //UpdateNSamplesExtendedFab(0);

        animRotateButton = AnimationUtils.loadAnimation(requireContext(),R.anim.fab_rotate);
//
//        animRotateButton.setAnimationListener(new Animation.AnimationListener() {
//           @Override
//           public void onAnimationStart(Animation animation) {
//          }
//          @Override
//          public void onAnimationEnd(Animation animation) {
//               if(mNumSamplesForUploadLogButton!=0) {
//                   UpdateNSamplesExtendedFab(mNumSamplesForUploadLogButton);
//               }
//           }
//           @Override
//           public void onAnimationRepeat(Animation animation) {
//            }
//        });

        animRotateBackButton = AnimationUtils.loadAnimation(requireContext(),R.anim.fab_rotate_back);

        mCloudClientSpinner = root.findViewById(R.id.cloudProviderSpinner);
        setUpCloudClientSpinner(mCloudClientSpinner,getNode(), savedInstanceState);
        mCloudConfig = root.findViewById(R.id.cloudProviderConfigView);

        mDataPageLink = root.findViewById(R.id.openCloudPageButton);
        setUpDataPageLink(mDataPageLink);

        mShowDetailsButton = root.findViewById(R.id.showDetailsButton);
        setUpDetailsButton(mShowDetailsButton);

        mCloudConnectionProgress = root.findViewById(R.id.cloudConnectionProgress);
        mCloudConnectionProgress.setVisibility(View.GONE);

        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Log.i("CloudFragment","onSaveInstanceState");
        super.onSaveInstanceState(outState);
        if(mCloudClientSpinner != null) {
            outState.putInt(SELECTED_CLOUD_KEY, mCloudClientSpinner.getSelectedItemPosition());
        }
        if(mUpoladLogButton.isShown()) {
            outState.putBoolean(UPLOAD_BUTTON_VISIBLE_KEY, mUpoladLogButton.isShown());
        }

        outState.putInt(UPLOAD_BUTTON_NUM_SAMPLES_KEY, mNumSamplesForUploadLogButton);
    }

    private void restoreCloudClientParamState(Bundle savedInstanceState) {
        Log.i("CloudFragment","restoreCloudClientParamState");
        int selected = savedInstanceState.getInt(SELECTED_CLOUD_KEY);
        mCloudClientSpinner.setSelection(selected);

        if(savedInstanceState.getBoolean(UPLOAD_BUTTON_VISIBLE_KEY)) {
            mUpoladLogButton.show();
        } else {
            mUpoladLogButton.hide();
        }

        mNumSamplesForUploadLogButton = savedInstanceState.getInt(UPLOAD_BUTTON_NUM_SAMPLES_KEY);
        UpdateNSamplesExtendedFab(mNumSamplesForUploadLogButton);
    }


    private void setUpDetailsButton(Button showDetailsButton) {
        showDetailsButton.setOnClickListener(view -> {
            changeEnableState(mCloudConfig,false);
            if(mCloudConfig.getVisibility()==View.VISIBLE){
                mCloudConfig.setVisibility(View.GONE);
            }else
                mCloudConfig.setVisibility(View.VISIBLE);
        });
    }

    private void setUpDataPageLink(Button dataPageLink) {
        dataPageLink.setOnClickListener(view -> {
            Uri page = mCloudConnectionFactory.getDataPage();
            if(page!=null) {
                Activity activity = requireActivity();
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, page));
                }catch (ActivityNotFoundException e){
                    Toast.makeText(activity, R.string.cloudLog_browserNotFound, Toast.LENGTH_SHORT).show();
                }
            }
        });
        dataPageLink.setVisibility(View.GONE);
    }

    /**
     * atach the adapter to the spinner and set the selectedListener
     * @param spinner spinner to set up
     */
    private void setUpCloudClientSpinner(Spinner spinner, @Nullable final Node node, @Nullable Bundle savedState){
        spinner.setAdapter(new MqttClientConfigAdapter(getActivity(), mCloudProviders));

        Log.i("CloudFragment","setUpCloudClientSpinner");
        /*
         * remove the previous view and add the new views for configure the selected service
         */
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                FragmentManager fm = getChildFragmentManager();
                CloudIotClientConfigurationFactory nextConf = (CloudIotClientConfigurationFactory) adapterView.getItemAtPosition(i);
                if(nextConf!=mCloudConfigurationFactory){
                    //remove the old view
                    if(mCloudConfigurationFactory!=null)
                        mCloudConfigurationFactory.detachParameterConfiguration(fm,mCloudConfig);
                    mCloudConfigurationFactory = nextConf;
                }
                //add the new view and set the current node
                if(mCloudConfigurationFactory!=null) {
                    mCloudConfigurationFactory.attachParameterConfiguration(fm, mCloudConfig,mMCU_id);
                    mCloudConfigurationFactory.loadDefaultParameters(fm, node);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        if(savedState!=null){
            restoreCloudClientParamState(savedState);
        }
    }


    private void setUpStartLogButton(ImageButton button) {
        Log.i("CloudFragment","setUpStartLogButton");
        button.setOnClickListener(this::onStartStopCloudLogClick);
        if (isOnline()) {
            enableCloudButton();
        }else{
            disableCloudButton();
        }
    }

    /**
     * true if we have a connection with the cloud service
     * @return true if the connection with the cloud service is open, false otherwise
     */
    private boolean isCloudConnected() {
        Log.i("CloudFragment","isCloudConnected");
        return mCloudConnectionFactory != null && mMqttClient!=null &&
                mCloudConnectionFactory.isConnected(mMqttClient);
    }


    /**
     * set up the fragment views to display when the connection is open
     */
    private void showConnectedView() {
        Log.i("CloudFragment","showConnectedView");
        mStartLogButton.setImageResource(R.drawable.ic_cloud_upload_stop_24dp);
        if((mNumSamplesForUploadLogButton==0) && (animRotateButton!=null)){
            mStartLogButton.startAnimation(animRotateButton);
        }
        disableCloudConfiguration();
        hideConnectingView();
        mCloudConnectionProgress.setVisibility(View.GONE);
        mShowDetailsButton.setVisibility(View.VISIBLE);
        mCloudConfig.setVisibility(View.GONE);
        mFeatureListView.setVisibility(View.VISIBLE);
        mFeatureListView.setAdapter(new FeatureListViewAdapter(getSupportedFeatures(mNode.getFeatures()),mSendDataToCloudWhenSelected));
        if(mCloudConnectionFactory.getDataPage()!=null)
            mDataPageLink.setVisibility(View.VISIBLE);

        if (mCloudConnectionFactory.showUploadButton()) {
            UpdateNSamplesExtendedFab(mNumSamplesForUploadLogButton);
            mUpoladLogButton.show();
        }
    }

    /**
     * set up the fragment views to display when the connection is closed
     */
    private void showDisconnectedView() {
        Log.i("CloudFragment","showDisconnectedView");
        mStartLogButton.setImageResource(R.drawable.ic_cloud_upload_24dp);
        if(animRotateBackButton!=null) {
            mStartLogButton.startAnimation(animRotateBackButton);
        }
        enableCloudConfiguration();
        mFeatureListView.setVisibility(View.GONE);
        mDataPageLink.setVisibility(View.GONE);
        mShowDetailsButton.setVisibility(View.GONE);
        mCloudConfig.setVisibility(View.VISIBLE);
        UpdateNSamplesExtendedFab(0);
    }

    private void showConnectingView(){
        mStartLogButton.setEnabled(false);
        mCloudConnectionProgress.setVisibility(View.VISIBLE);
    }

    private void hideConnectingView(){
        mStartLogButton.setEnabled(true);
        mCloudConnectionProgress.setVisibility(View.GONE);
    }

    /**
     * open a connection to the selected cloud service
     */
    private void startCloudConnection() {
        Log.i("CloudFragment","startCloudConnection");
        if(isCloudConnected()) {
            return;
        }

        if(mCloudConnectionFactory!=null && mMqttClient!=null){
            mCloudConnectionFactory.destroy(mMqttClient);
        }

        try {
            mCloudConnectionFactory = mCloudConfigurationFactory.getConnectionFactory(getChildFragmentManager());
        }catch (IllegalArgumentException e){
            buildMqttErrorDialog(CloudLogFragment.this.getActivity(), e.getMessage()).show();
            Log.e(getClass().getName(),"Error: "+e.getMessage());
            return;
        }

        if (mCloudConnectionFactory.showUploadButton()) {
//            mUpoladLogButton.setVisibility(View.VISIBLE);
//            mUpoladLogButton.startAnimation(animScaleInButton);

            mUpoladLogButton.show();
            UpdateNSamplesExtendedFab(mNumSamplesForUploadLogButton);

            mNewSampleListener = new CloudIotClientConnectionFactory.NewSampleListener() {
                @Override
                public void onNewSampleReady(int numSamples) {
                    UpdateNSamplesExtendedFab(numSamples);
                }
            };
        }

        try {
            mMqttClient = mCloudConnectionFactory.createClient(requireActivity());
            Context ctx = requireContext();
            final Context appContext = ctx.getApplicationContext();
            showConnectingView();
            mCloudConnectionFactory.connect(ctx, mMqttClient, new CloudIotClientConnectionFactory.ConnectionListener() {
                @Override
                public void onSuccess() {
                    mCloudConnectionFactory.setNewSampleListener(mMqttClient,mNewSampleListener);
                    mCloudLogListener = mCloudConnectionFactory.getFeatureListener(mMqttClient,getUpdateInterval());
                    mCloudConnectionFactory.enableCloudFwUpgrade(mNode, mMqttClient, fwUrl -> {
                        Uri firmwareRemoteLocation = Uri.parse(fwUrl);
                        DownloadFwFileService.displayAvailableFwNotification(appContext,firmwareRemoteLocation);
                    });
                    updateGui(() -> showConnectedView());
                }

                private String getErrorMessage(final Throwable exception){
                    Throwable e = exception.getCause();
                    if(e!=null){
                        return e.toString();
                    }
                    return exception.getLocalizedMessage();
                }

                @Override
                public void onFailure(final Throwable exception) {
                    updateGui(() -> {
                        buildMqttErrorDialog(CloudLogFragment.this.getActivity(), getErrorMessage(exception)).show();
                        hideConnectingView();
                    });
                    exception.printStackTrace();
                }
            });
            mFwDownloadReceiver.registerReceiver();
        } catch (Exception e) {
            e.printStackTrace();
            buildMqttErrorDialog(getActivity(), e.getMessage()).show();
        }
    }

    /**
     * remove the resource used by the mqtt
     */
    @Override
    public void onDestroy() {
        Log.i("CloudFragment","onDestroy");
        super.onDestroy();
        if(mCloudConnectionFactory!=null && mMqttClient!=null)
            mCloudConnectionFactory.destroy(mMqttClient);

        UpdateNSamplesExtendedFab(0);
    }

    private void stopAllNotification() {
        Log.i("CloudFragment","stopAllNotification");
        if(mNode==null) // avoid NPE, in some devices..
            return;
        //no active notification in this demo, so nothing to do
        if(mNUserSelectedFeature == 0)
            return ;
        for (Feature f : mNode.getFeatures()) {
            if (mNode.isEnableNotification(f)) {
                f.removeFeatureListener(mCloudLogListener);
                mNode.disableNotification(f);
            }//if enabled
        }//for
        mNUserSelectedFeature = 0;

        //update the gui for show our changes
        RecyclerView.Adapter a = mFeatureListView.getAdapter();
        if (a != null)
            a.notifyDataSetChanged();
    }//stopAllNotification

    /**
     * close the cloud connection
     */
    private void closeCloudConnection() {
        Log.i("CloudFragment","closeCloudConnection");
        if (isCloudConnected()) { //cloudConnected check that Mqtt Client is not null
            mCloudLogListener = null;
            Context ctx = getActivity();
            if(ctx!=null) // can be null
                mFwDownloadReceiver.unregisterReceiver();
            try {
                mCloudConnectionFactory.disconnect(mMqttClient);
            } catch (Exception e) {
                buildMqttErrorDialog(getActivity(), e.getMessage()).show();
            }//try-catch

            if (mCloudConnectionFactory.showUploadButton()) {
                //mUpoladLogButton.startAnimation(animScaleOutButton);
                mUpoladLogButton.hide();
            }

        }//if
    }//closeCloudConnection

    /**
     * function called when the connection button is pressed
     * @param v button pressed
     */
    private void onStartStopCloudLogClick(View v) {
        Log.i("CloudFragment","onStartStopCloudLogClick");
        if (!isCloudConnected()) {
            startCloudConnection();
        } else {
            stopAllNotification();
            closeCloudConnection();
            showDisconnectedView();
        }//if-else
    }

    /**
     * function called when the upload button is pressed
     * @param v button pressed
     */
    private void onUploadCloudLogClick(View v) {
        Log.i("CloudFragment","onUploadCloudLogClick");
        stopAllNotification();
        mCloudConnectionFactory.upload(requireContext(),mMqttClient);
    }

    /**
     * utility function for change the state of a view and its child
     * @param v view to change
     * @param enabled new state
     */
    private static void changeEnableState(View v, boolean enabled) {
        if (v instanceof ViewGroup) { //if it has child, change the child
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                changeEnableState(vg.getChildAt(i), enabled);
            }
        }
        v.setEnabled(enabled);
    }

    @Override
    protected void onSystemLostConnectivity() {
        super.onSystemLostConnectivity();
        stopAllNotification();
        showDisconnectedView();
        disableCloudButton();
    }

    @Override
    protected void onSystemHasConnectivity() {
        super.onSystemHasConnectivity();
        Log.i("CloudFragment","onSystemHasConnectivity");
        enableCloudButton();
    }


    private void disableCloudConfiguration() {
        Log.i("CloudFragment","disableCloudConfiguration");
        mCloudClientSpinner.setEnabled(false);
        changeEnableState(mCloudConfig, false);
        mFeatureListViewContainer.setVisibility(View.VISIBLE);
    }

    private void enableCloudConfiguration() {
        Log.i("CloudFragment","enableCloudConfiguration");
        mCloudClientSpinner.setEnabled(true);
        changeEnableState(mCloudConfig, true);
        mFeatureListViewContainer.setVisibility(View.GONE);

    }

    private void enableCloudButton() {
        //only the first time change the icon
        Log.i("CloudFragment","enableCloudButton");
        if(!mStartLogButton.isEnabled()) {
            mStartLogButton.setEnabled(true);
            mStartLogButton.setImageResource(R.drawable.ic_cloud_upload_24dp);
        }
    }

    private void disableCloudButton() {
        Log.i("CloudFragment","disableCloudButton");
        mStartLogButton.setEnabled(false);
        mStartLogButton.setImageResource(R.drawable.ic_cloud_offline_24dp);
        if(animRotateBackButton!=null) {
            mStartLogButton.startAnimation(animRotateBackButton);
        }
    }

    @Override
    public void onFwUpgradeAvailable(String fwUrl) {

        DialogFragment requestDialog = CloudFwUpgradeRequestDialog.create(fwUrl);
        requestDialog.show(getChildFragmentManager(),"requestFwUpgrade");

    }

    /**
     * start the service to download the a file, this function is using the DownloadManager,
     * when the download ends an broadcast message is fired and captured by DownloadFwFileCompletedReceiver class
     * @param fwUri file url to download
     * @return request id
     */
    private long downloadFile(String fwUri){
        DownloadManager.Request dwRequest = new DownloadManager.Request(Uri.parse(fwUri));
        dwRequest.setTitle(getString(R.string.cloudLog_downloadFw_title));
        dwRequest.setDescription(getString(R.string.cloudLog_downloadFw_desc,mNode.getName()));
        dwRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        DownloadManager manager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        return  manager.enqueue(dwRequest);
    }

    @Override
    public void onCloudFwUpgradeRequestAccept(String fwUri) {
        downloadFile(fwUri);
    }

    @Override
    public void onCloudFwUpgradeRequestDelcine() {

    }

    private void storeUpdateInterval(int updateInterval){
        requireActivity().getSharedPreferences(CONF_PREFIX_KEY,Context.MODE_PRIVATE).edit()
                    .putInt(UPDATE_INTERVAL_KEY,updateInterval)
                    .apply();

    }

    private int getUpdateInterval(){
        return requireActivity().getSharedPreferences(CONF_PREFIX_KEY,Context.MODE_PRIVATE)
                .getInt(UPDATE_INTERVAL_KEY,DEFAULT_UPDATE_INTERVAL_MS);
    }

    @Override
    public void onNewUpdateIntervalSelected(int newUpdateInterval) {
        storeUpdateInterval(newUpdateInterval);
    }
}