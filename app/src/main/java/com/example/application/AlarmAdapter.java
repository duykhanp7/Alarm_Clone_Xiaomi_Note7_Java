package com.example.application;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.ViewHolder> {

    @SuppressLint("StaticFieldLeak")
    public static Context context;
    private final AlarmFragment mAlarmFragment;
    private final MainActivity mMainActivityFromParent;
    private final SaveDataToSQLite saveDataToSQLite;
    private final Intent intentMoveToFixAlarm;
    Timer timer;
    TimerTask timerTask;
    List<ViewHolder> listItemAlarm = new ArrayList<>();
    int aCountDown;
    private List<TimeElement> timeElementList = new ArrayList<>();
    private View viewAlarmSettingsDialog;
    private Dialog alarmSettingsDialog;
    private SwitchMaterial switchMaterialOnOffDialog;
    private TimePicker timePickerAlarmDialog;
    private TextView buttonCompletedEdit;
    private TextView textViewHourDialog;
    private TextView textViewNoteDialog;
    private TextView textViewRegularDialog;
    private Calendar calendarDialog;
    private String textHour, textMinute;
    private int positionItemCurrent = 0;
    private Toast toastTimeRemain;
    private TimeElement oldClock;
    private boolean before, checkHasShowToast = false, checkOnBackPressed = false, checkShowAllCheckBox = false;
    private boolean checkBeingLongClick = false, checkFromFixAlarm = false;
    private boolean isRetrieveFromDatabase = false;

    //H??M KH???I T???O
    public AlarmAdapter(List<TimeElement> timeElementList, Context context, MainActivity mainActivity, AlarmFragment alarmFragment) {
        this.timeElementList = timeElementList;
        AlarmAdapter.context = context;
        this.mMainActivityFromParent = mainActivity;
        intentMoveToFixAlarm = new Intent(context, CreateAlarmActivity.class);
        this.mAlarmFragment = alarmFragment;
        initViewDialog();
        sortListAlarm(timeElementList);
        saveDataToSQLite = new SaveDataToSQLite(context);
        retrieveItemsFromDatabase();
        runTimeUpdateTimeRemainForSingleItemView();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel("9999", "Channel A", NotificationManager.IMPORTANCE_HIGH);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    public static Notification createNotification(TimeElement timeElementT) {
        //INTENT FOR BUTTON TAT
        Intent intentForButtonTat = new Intent(context, AlarmFragment.CustomBroadcast.class);
        Bundle bundleOff = new Bundle();

        bundleOff.putString("state", "off");
        bundleOff.putSerializable("timeEelement", timeElementT);
        bundleOff.putString("fromButtonTat", "true");
        intentForButtonTat.putExtra("bundle", bundleOff);
        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(context, timeElementT.getIdAlarm(), intentForButtonTat, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentClickInto = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        @SuppressLint({"LaunchActivityFromNotification", "UnspecifiedImmutableFlag"}) PendingIntent pendingIntentCLickInto = PendingIntent.getBroadcast(context, 1000, intentClickInto, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.custom_notification_layout);
        remoteViews.setTextViewText(R.id.textViewHourNotification, "B??o th???c s???p b??o: " + timeElementT.getHour() + ":" + timeElementT.getMinute());
        remoteViews.setTextViewText(R.id.textViewNoteNotification, "B???n c?? th??? t???t b??o th???c n??y b??y gi???");
        remoteViews.setOnClickPendingIntent(R.id.buttonTatAlarm, pendingIntent);

        @SuppressLint("LaunchActivityFromNotification") NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "9999")
                .setSmallIcon(R.drawable.meow)
                .setCustomContentView(remoteViews) // CUSTOM LAYOUT CHO TH??NG B??O
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_MAX)// th??? d??ng NotificationCompatManager v?? NotificationManager????
                .setContentIntent(pendingIntentCLickInto)
                .setAutoCancel(false)
                .setOngoing(false);

        return builder.build();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_layout_list_alarm, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint({"SetTextI18n", "UseCompatLoadingForDrawables"})
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        //CREATE NOTIFICATION IF TIME REMAIN IS NOT OVER 1 HOUR
        //AND ALLOW USERS TO TURN OFF IT
        TimeElement timeElement = timeElementList.get(position);
        holder.setTimeElement(timeElement);
        //ADD VIEW HOLDER TO LIST ITEM TO UPDATE TIME REMAIN VIEW
        listItemAlarm.add(holder);
        listItemAlarm.sort(new Comparator<ViewHolder>() {
            @Override
            public int compare(ViewHolder viewHolder, ViewHolder t1) {
                return viewHolder.timeElement.compareTwoObject(t1.timeElement);
            }
        });

        boolean stateAlarm = timeElement.getOnOrOff();

        boolean checkOver60Minutes = timeElement.getTimeCountdown().contains("gi???");

        if (!checkOver60Minutes && !isRetrieveFromDatabase && timeElement.getOnOrOff() && !timeElement.getTimeCountdown().isEmpty()) {
            createNotificationChannelAndNotification(timeElementList.get(holder.getAdapterPosition()));
        }
        checkHasShowToast = false;

        holder.switchOnOff.setVisibility(View.VISIBLE);
        holder.checkBoxSelectedDelItem.setVisibility(View.INVISIBLE);
        holder.checkBoxSelectedDelItem.setChecked(false);
        if (checkOnBackPressed) {
            checkHasShowToast = true;
            checkShowAllCheckBox = false;
        }

        if (checkShowAllCheckBox) {
            holder.switchOnOff.setVisibility(View.INVISIBLE);
            holder.checkBoxSelectedDelItem.setVisibility(View.VISIBLE);
            holder.checkBoxSelectedDelItem.setChecked(timeElement.getCheckedDelete());
            checkHasShowToast = true;
            checkOnBackPressed = false;
        }

        if (timeElement.getOnOrOff() && !checkHasShowToast) {
            toastTimeRemain = Toast.makeText(context, timeElement.getTimeCountdown(), Toast.LENGTH_LONG);
            toastTimeRemain.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 150);
            checkHasShowToast = true;
            if (!isRetrieveFromDatabase) {
                toastTimeRemain.show();
            }
        }

        holder.textViewHour.setText(timeElement.getHour() + ":" + timeElement.getMinute());
        holder.textViewNote.setText(String.valueOf(timeElement.getNote()));
        holder.textViewRegular.setText(timeElement.getRegular());

        if (timeElement.getTimeCountdown().isEmpty()) {
            holder.textViewTimeCountdown.setText(timeElement.getTimeCountdown());
        } else {
            holder.textViewTimeCountdown.setText(" | " + timeElement.getTimeCountdown());
        }

        ColorStateList color = holder.textViewNote.getTextColors();


        //M???C ?????NH TH?? B??O TH???C S??? ??? TR???NG TH??I B???T
        holder.switchOnOff.setChecked(stateAlarm);
        holder.textViewHour.setTextColor(Color.parseColor("#FF000000"));
        holder.switchOnOff.setBackground(holder.switchOnOff.getResources().getDrawable(R.drawable.custom_background_switch_on));
        //N???U ???????C SET T???T TH?? S??? ?????I SANG TR???NG TH??I T???T
        if (!stateAlarm) {
            holder.switchOnOff.setBackground(holder.switchOnOff.getResources().getDrawable(R.drawable.custom_background_switch_off));
            holder.textViewHour.setTextColor(color);
            checkHasShowToast = false;
        }
        //M???C ?????NH TH?? TOAST S??? L?? FALSE(CH??A SHOW TIMEREMAIN)

        //N??T SWITCH B???T T???T B??O TH???C
        holder.switchOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @SuppressLint("UseCompatLoadingForDrawables")
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                TimeElement timeElementItemClicked = timeElementList.get(holder.getAdapterPosition());
                String text = timeElementItemClicked.getTimeCountdown();

                isRetrieveFromDatabase = false;
                //N???U SWITCH ON V?? TOAST CH??A SHOW
                String timeRemain = "";
                SharedPreferences sharedPreferences = context.getSharedPreferences("metadata", Context.MODE_PRIVATE);
                if (b) {
                    Calendar calendarCurrent = Calendar.getInstance();
                    Calendar calendarTimeSet = Calendar.getInstance();
                    calendarTimeSet.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeElementItemClicked.getHour()));
                    calendarTimeSet.set(Calendar.MINUTE, Integer.parseInt(timeElementItemClicked.getMinute()));

                    long timeRemainSub = calendarTimeSet.getTime().getTime() - calendarCurrent.getTime().getTime();

                    if (timeRemainSub <= 0) {
                        calendarTimeSet.add(Calendar.DATE, 1);
                        Date dateNew = calendarTimeSet.getTime();
                        timeRemainSub = dateNew.getTime() - calendarCurrent.getTime().getTime();
                    }

                    int hours = (int) ((timeRemainSub / (1000 * 60 * 60)) % 24);
                    int minutes = (int) ((timeRemainSub / (1000 * 60)) % 60);

                    timeRemain = calculateTimeRemainToString(timeElementItemClicked.getHour(), timeElementItemClicked.getMinute());

                    timeElementItemClicked.setStateOnOrOff(true);
                    timeElementList.get(holder.getAdapterPosition()).setStateOnOrOff(true);

                    if (text.isEmpty()) {
                        holder.textViewTimeCountdown.setText(" | " + timeRemain);
                        timeElementItemClicked.setTimeCountdown(timeRemain);
                        timeElementList.get(holder.getAdapterPosition()).setTimeCountdown(timeRemain);
                    } else {
                        holder.textViewTimeCountdown.setText(" | " + text);
                        timeElementItemClicked.setTimeCountdown(text);
                        timeElementList.get(holder.getAdapterPosition()).setTimeCountdown(text);
                    }

                    holder.switchOnOff.setBackground(holder.switchOnOff.getResources().getDrawable(R.drawable.custom_background_switch_on));
                    holder.textViewHour.setTextColor(Color.parseColor("#FF000000"));


                    boolean checkOver60Minutes = timeElementItemClicked.getTimeCountdown().contains("gi???");

                    if (!checkOver60Minutes && !timeElementItemClicked.getTimeCountdown().isEmpty()) {

                        createNotificationChannelAndNotification(timeElementList.get(holder.getAdapterPosition()));
                    }

                    String checkTrueFalse = sharedPreferences.getString("fromButtonTatDialog", "true");
                    boolean check = checkTrueFalse.equals("true");
                    if (!check) {
                        Thread a;
                        a = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                updateInDatabaseAndCancelOrCreatePendingIntent(1, timeElementItemClicked, calendarTimeSet.getTimeInMillis());
                            }
                        });
                        a.start();
                        try {
                            a.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Thread a;
                        if (check && timeElementItemClicked.getOnOrOff()) {
                            a = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    updateInDatabaseAndCancelOrCreatePendingIntent(1, timeElementItemClicked, calendarTimeSet.getTimeInMillis());
                                }
                            });
                            a.start();
                            try {
                                a.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (b && !checkHasShowToast) {
                    toastTimeRemain = Toast.makeText(context, timeElementItemClicked.getTimeCountdown(), Toast.LENGTH_LONG);
                    toastTimeRemain.setGravity(Gravity.BOTTOM, 0, 150);
                    toastTimeRemain.show();
                }
                //C??C TR?????NG H???P KH??C TH?? L?? T???T B??O TH???C
                if (!b) {

                    NotificationManagerCompat.from(context).cancel(timeElementItemClicked.getIdAlarm());
                    String checkTrueFalse = sharedPreferences.getString("fromButtonTatDialog", "true");
                    boolean check = checkTrueFalse.equals("true");

                    timeElementItemClicked.setStateOnOrOff(false);
                    timeElementList.get(holder.getAdapterPosition()).setStateOnOrOff(false);
                    timeElementItemClicked.setTimeCountdown("");
                    timeElementList.get(holder.getAdapterPosition()).setTimeCountdown("");

                    holder.switchOnOff.setBackground(holder.switchOnOff.getResources().getDrawable(R.drawable.custom_background_switch_off));
                    holder.textViewHour.setTextColor(color);
                    holder.textViewTimeCountdown.setText("");


                    if (!check) {
                        Thread a;
                        a = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                updateInDatabaseAndCancelOrCreatePendingIntent(0, timeElementItemClicked, 0);
                            }
                        });
                        a.start();
                        try {
                            a.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Cursor cursor = new SaveDataToSQLite(context).queryToGetDataReturn("SELECT * FROM " + SaveDataToSQLite.TABLE_NAME_PENDING_INTENT +
                                " WHERE " +
                                SaveDataToSQLite.COLUMN_NAME_REQUEST_CODE + " = " + timeElementItemClicked.getIdAlarm());
                        cursor.moveToFirst();
                        String dataBlobConvertToBlob = Arrays.toString(cursor.getBlob(1)).replace("[]", "");
                        boolean stateID = timeElementItemClicked.getOnOrOff();
                        if (!dataBlobConvertToBlob.isEmpty()) {
                            Thread a;
                            a = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    updateInDatabaseAndCancelOrCreatePendingIntent(0, timeElementItemClicked, 0);
                                }
                            });
                            a.start();
                            try {
                                a.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("fromButtonTatDialog", "false");
                        editor.commit();
                    }
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("fromButtonTatDialog", "false");
                editor.commit();
                checkFromFixAlarm = false;
                checkHasShowToast = false;
            }

        });
        //SET ONCLICK CHO T???NG C??I B??O TH???C
        holder.itemAlarmLayout.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("UseCompatLoadingForDrawables")
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                isRetrieveFromDatabase = false;
                if (checkShowAllCheckBox) {
                    holder.checkBoxSelectedDelItem.setChecked(!holder.checkBoxSelectedDelItem.isChecked());
                    timeElementList.get(holder.getAdapterPosition()).setCheckedDelete(holder.checkBoxSelectedDelItem.isChecked());
                    checkOnBackPressed = false;
                } else {
                    checkHasShowToast = false;
                    checkOnBackPressed = false;
                    checkShowAllCheckBox = false;
                    positionItemCurrent = holder.getAdapterPosition();
                    textViewHourDialog.setText(timeElement.getHour() + ":" + timeElement.getMinute());
                    textViewNoteDialog.setText(String.valueOf(timeElement.getNote()));
                    if (!timeElement.getOnOrOff()) {
                        textViewRegularDialog.setText(timeElement.getRegular());
                    } else {
                        textViewRegularDialog.setText(calculateTimeRemainToString(timeElement.getHour(), timeElement.getMinute()));
                    }
                    timePickerAlarmDialog.setHour(Integer.parseInt(timeElement.getHour()));
                    timePickerAlarmDialog.setMinute(Integer.parseInt(timeElement.getMinute()));

                    before = holder.switchOnOff.isChecked();
                    if (holder.switchOnOff.isChecked()) {
                        timeElement.setStateOnOrOff(true);
                        switchMaterialOnOffDialog.setChecked(true);
                        switchMaterialOnOffDialog.setBackground(switchMaterialOnOffDialog.getResources().getDrawable(R.drawable.custom_background_switch_on));
                        switchMaterialOnOffDialog.setTextColor(Color.parseColor("#FF000000"));
                        textViewHourDialog.setTextColor(Color.parseColor("#FF000000"));
                    } else {
                        timeElement.setStateOnOrOff(false);
                        switchMaterialOnOffDialog.setChecked(false);
                        switchMaterialOnOffDialog.setBackground(switchMaterialOnOffDialog.getResources().getDrawable(R.drawable.custom_background_switch_off));
                        switchMaterialOnOffDialog.setTextColor(color);
                        textViewHourDialog.setTextColor(Color.parseColor("#BABDBF"));
                    }
                    //T???O 1 B??O TH???C C?? ????? ??EM SO S??NH THAY ?????I KHI T???O B??O TH???C M???I
                    oldClock = timeElement;
                    checkHasShowToast = false;
                    checkShowAllCheckBox = false;
                    checkOnBackPressed = false;
                    alarmSettingsDialog.show();
                    mMainActivityFromParent.hideFloatingButtonAddAlarm(View.INVISIBLE);
                }
            }
        });
        holder.itemAlarmLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                boolean checked = holder.checkBoxSelectedDelItem.isChecked();
                if (!checkBeingLongClick) {

                    checkBeingLongClick = true;

                    mMainActivityFromParent.hideFloatingButton();
                    mMainActivityFromParent.showLayoutDeleteAlarm();
                    mMainActivityFromParent.showTopAppBarLayout(true);
                    mMainActivityFromParent.enableButtonCancelAndSelectAll();

                    holder.checkBoxSelectedDelItem.setChecked(!checked);
                    holder.checkBoxSelectedDelItem.setVisibility(View.VISIBLE);
                    holder.switchOnOff.setVisibility(View.INVISIBLE);
                    timeElementList.get(holder.getAdapterPosition()).setCheckedDelete(!checked);
                    checkShowAllCheckBox = true;
                    checkOnBackPressed = false;
                    notifyItemRangeChanged(0, timeElementList.size());
                } else {
                    holder.checkBoxSelectedDelItem.setChecked(!checked);
                    timeElementList.get(holder.getAdapterPosition()).setCheckedDelete(!checked);
                }
                return true;
            }
        });
        checkHasShowToast = false;
    }

    @Override
    public int getItemCount() {
        return timeElementList.size();
    }

    //TH??M B??O TH???C V??O LIST V?? C???P NH???T L???I UI
    public void addTimeElement(TimeElement insertElement, int INT_ADD_OR_FIX_ALARM, int POSITION_ALARM_FIX) {
        isRetrieveFromDatabase = false;
        checkOnBackPressed = false;
        checkShowAllCheckBox = false;
        mAlarmFragment.hideEmptyAlarmIcon(true);
        //N???U TR???NG TH??I L?? TH??M B??O TH???C
        if (INT_ADD_OR_FIX_ALARM == TimeElement.STATE_ADD_ALARM) {
            timeElementList.add(insertElement);
            //N???U S??? L?????NG B??O TH???C L???N H??N 1 TH?? M???I SORT KH??NG TH?? TH??I
            if (timeElementList.size() != 1) {
                sortListAlarm(timeElementList);
            }
            notifyItemInserted(timeElementList.indexOf(insertElement));
            if (timeElementList.size() != 1) {
                mAlarmFragment.scrollToPosition(timeElementList.indexOf(insertElement));
            }
        }
        //N???U TR???NG TH??I L?? S???A B??O TH???C
        else if (INT_ADD_OR_FIX_ALARM == TimeElement.STATE_FIX_ALARM) {
            //N???U S??? L?????NG B??O TH???C L???N H??N 1
            if (timeElementList.size() != 1) {
                timeElementList.remove(POSITION_ALARM_FIX);
                notifyItemRemoved(POSITION_ALARM_FIX);
                timeElementList.add(insertElement);
                sortListAlarm(timeElementList);
                notifyItemInserted(timeElementList.indexOf(insertElement));
                mAlarmFragment.scrollToPosition(timeElementList.indexOf(insertElement));
            } else {
                timeElementList.set(POSITION_ALARM_FIX, insertElement);
                sortListAlarm(timeElementList);
                notifyItemChanged(POSITION_ALARM_FIX);
            }
        }
        timeElementList.sort(new Comparator<TimeElement>() {
            @Override
            public int compare(TimeElement a, TimeElement b) {
                return a.compareTwoObject(b);
            }
        });
        checkHasShowToast = false;
    }

    //KH???I T???O VIEW V?? ??NH X??? CHO DIALOG CH???NH S???A B??O TH???C
    public void initViewDialog() {
        View viewAlarmSettingsDialog = LayoutInflater.from(context).inflate(R.layout.custom_dialog_for_item_clicked, null);
        textViewHourDialog = viewAlarmSettingsDialog.findViewById(R.id.textViewHourDialog);
        textViewNoteDialog = viewAlarmSettingsDialog.findViewById(R.id.textViewNoteDialog);
        textViewRegularDialog = viewAlarmSettingsDialog.findViewById(R.id.textViewRegularDialog);
        //textViewTimeRemainDialog = viewAlarmSettingsDialog.findViewById(R.id.textViewTimeRemainDialog);
        ColorStateList color = textViewRegularDialog.getTextColors();
        switchMaterialOnOffDialog = viewAlarmSettingsDialog.findViewById(R.id.switchOnOffDialog);
        timePickerAlarmDialog = viewAlarmSettingsDialog.findViewById(R.id.timePickerAlarmDialog);
        timePickerAlarmDialog.setIs24HourView(true);
        TextView buttonAddSettings = viewAlarmSettingsDialog.findViewById(R.id.buttonAddSettings);
        buttonCompletedEdit = viewAlarmSettingsDialog.findViewById(R.id.buttonCompletedEdit);

        alarmSettingsDialog = new Dialog(context, R.style.BottomSheetThemeCustom);


        alarmSettingsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                isRetrieveFromDatabase = false;
                checkFromFixAlarm = false;
                mMainActivityFromParent.hideFloatingButtonAddAlarm(View.VISIBLE);
                positionItemCurrent = -1;
            }
        });
        //?????NH NGH??A CLICK CHO T???NG TH??NH PH???N C???A DIALOG
        switchMaterialOnOffDialog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    switchMaterialOnOffDialog.setBackground(switchMaterialOnOffDialog.getResources().getDrawable(R.drawable.custom_background_switch_on));
                    switchMaterialOnOffDialog.setTextColor(Color.parseColor("#FF000000"));
                    textViewHourDialog.setTextColor(Color.parseColor("#FF000000"));
                    String hours = textViewHourDialog.getText().toString().split(":")[0];
                    String minutes = textViewHourDialog.getText().toString().split(":")[1];
                    textViewRegularDialog.setText("");
                    textViewRegularDialog.setText(calculateTimeRemainToString(hours, minutes));
                } else {
                    switchMaterialOnOffDialog.setBackground(switchMaterialOnOffDialog.getResources().getDrawable(R.drawable.custom_background_switch_off));
                    switchMaterialOnOffDialog.setTextColor(color);
                    textViewHourDialog.setTextColor(Color.parseColor("#BABDBF"));
                    textViewRegularDialog.setText(oldClock.getRegular());
                }
            }
        });
        //SET ONCLICK CHO TIMEPICKER C???A DIALOG
        timePickerAlarmDialog.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onTimeChanged(TimePicker timePicker, int hourChange, int minuteChange) {
                if (hourChange < 10) {
                    textHour = "0" + hourChange;
                } else {
                    textHour = String.valueOf(hourChange);
                }
                if (minuteChange < 10) {
                    textMinute = "0" + minuteChange;
                } else {
                    textMinute = String.valueOf(minuteChange);
                }
                Calendar calendarCurrent = Calendar.getInstance();
                Calendar calendarFromChange = Calendar.getInstance();
                calendarFromChange.set(Calendar.HOUR_OF_DAY, hourChange);
                calendarFromChange.set(Calendar.MINUTE, minuteChange);
                Date currentTime = calendarCurrent.getTime();
                Date newDate = calendarFromChange.getTime();
                int hourChangeE, minuteChangeE;
                //KI???M TRA TH???I GIAN C?? THAY ?????I HAY KH??NG
                long milliseconds = newDate.getTime() - currentTime.getTime();
                if (milliseconds <= 0) {
                    calendarFromChange.add(Calendar.DATE, 1);
                    Date dateNew = calendarFromChange.getTime();
                    milliseconds = dateNew.getTime() - currentTime.getTime();
                }
                hourChangeE = (int) ((milliseconds / (1000 * 60 * 60)) % 24);
                minuteChangeE = (int) ((milliseconds / (1000 * 60)) % 60);


                if (switchMaterialOnOffDialog.isChecked()) {
                    if (hourChangeE == 0) {
                        textViewRegularDialog.setText("B??o th???c sau " + minuteChangeE + " ph??t");
                    } else {
                        if (minuteChangeE == 0) {
                            textViewRegularDialog.setText("B??o th???c sau " + hourChangeE + " gi??? ");
                        } else {
                            textViewRegularDialog.setText("B??o th???c sau " + hourChangeE + " gi??? " + minuteChangeE + " ph??t");
                        }
                    }
                }
                textViewHourDialog.setText(textHour + ":" + textMinute);
            }
        });

        //N??T M??? PH???N CH???NH S???A B??O TH???C
        buttonAddSettings.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                String state = null;
                int hourTemp, minuteTemp;
                hourTemp = timePickerAlarmDialog.getHour();
                minuteTemp = timePickerAlarmDialog.getMinute();

                intentMoveToFixAlarm.putExtra("MODE_ADD_ALARM", 2);
                intentMoveToFixAlarm.putExtra("TIME_ELEMENT", timeElementList.get(positionItemCurrent));
                intentMoveToFixAlarm.putExtra("STATE_ALARM", TimeElement.STATE_FIX_ALARM);
                intentMoveToFixAlarm.putExtra("STATE_ALARM_VIBRATE", String.valueOf(timeElementList.get(positionItemCurrent).getVibrate()));
                intentMoveToFixAlarm.putExtra("POSITION", positionItemCurrent);
                intentMoveToFixAlarm.putExtra("TEMP_HOUR_MINUTE", hourTemp + ":" + minuteTemp);


                if (switchMaterialOnOffDialog.isChecked()) {
                    state = "on";
                } else {
                    state = "off";
                }
                intentMoveToFixAlarm.putExtra("STATE", state);
                mMainActivityFromParent.mResultLauncher.launch(intentMoveToFixAlarm);
                checkHasShowToast = false;
                alarmSettingsDialog.dismiss();
            }
        });

        //N??T ???? HO??N TH??NH CH???NH S???A B??O TH???C
        buttonCompletedEdit.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                isRetrieveFromDatabase = false;

                TimeElement timeElementNew = null;

                boolean onOff = switchMaterialOnOffDialog.isChecked();
                //boolean vibrate = switchVi
                // L???I ??? CH??? N??Y, N???U CH??? L?????T THAY ?????I GI??? V?? KH??NG ON SWITCH TH?? N??
                //KH??NG HI???N TEXT TIME REMAIN GI??? THAY ?????I NH??NG DO kh??ng HI???N TEXT N??N G???P L???I
                //KI???M TRA XEM V???A THAY ?????I TH???I GIAN TH?? V???N SET TEXT NG???M
                String textTimeRemain = textViewRegularDialog.getText().toString();
                if (!textTimeRemain.contains("sau")) {
                    textTimeRemain = "";
                }
                //T???O RA 1 B??O TH??C M???I
                timeElementNew = createTimeElement(
                        String.valueOf(textViewHourDialog.getText().toString().split(":")[0]),
                        String.valueOf(textViewHourDialog.getText().toString().split(":")[1]),
                        textViewNoteDialog.getText().toString(),
                        oldClock.getRegular(),
                        textTimeRemain,
                        onOff, timeElementList.get(positionItemCurrent).getVibrate());
                timeElementNew.setIdAlarm(oldClock.getIdAlarm());

                //("AAA","TIME REMAIN TEXTVIEW : "+timeElementNew.getTimeCountdown());
                //SO S??NH B??O TH???C C?? V?? B??O TH???C M???I C?? G?? THAY ?????I KH??NG
                //N???U C?? TR??? V??? TRUE ELSE RETURN FALSE

                //KI???M TRA XEM 2 B??O TH???C N??Y GI???NG NHAU T???T C??? V?? CH??? KH??C STATE ON/OFF ????NG HAY SAI
                //TR??? V??? TRUE N???U ????NG V?? TR??? V??? FALSE KHI SAI
                boolean checkJustDiffOnOff = checkJustDiffOnOffFunction(oldClock, timeElementNew);
                boolean checkDiffTime = !timeElementNew.getHour().equals(oldClock.getHour()) || !oldClock.getMinute().equals(timeElementNew.getMinute());
                int state = 0;
                //N???U B??O TH???C C?? V?? M???I KH??C NHAU TH?? SHOW TOAST V?? SET TR???NG TH??I B???T CHO B??O TH???C M???I

                if (!oldClock.equals(timeElementNew)) {
                    checkFromFixAlarm = true;
                    if (onOff || checkDiffTime) {
                        timeElementNew.setStateOnOrOff(true);
                        if (checkDiffTime) {
                            timeElementNew.setTimeCountdown(calculateTimeRemainToString(timeElementNew.getHour(), timeElementNew.getMinute()));
                        }
                        state = 1;
                    } else {
                        if (!onOff && !checkDiffTime) {
                            timeElementNew.setTimeCountdown("");
                            timeElementNew.setStateOnOrOff(false);
                            state = 0;
                        }
                        NotificationManagerCompat.from(context).cancel(timeElementNew.getIdAlarm());
                    }
                    checkHasShowToast = false;

                    addTimeElement(timeElementNew, TimeElement.STATE_FIX_ALARM, positionItemCurrent);

                    //UPDATE IN DATABASE
                    Calendar calendarCurrent = Calendar.getInstance();
                    Calendar calendarSetTime = Calendar.getInstance();
                    calendarSetTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeElementNew.getHour()));
                    calendarSetTime.set(Calendar.MINUTE, Integer.parseInt(timeElementNew.getMinute()));
                    long timeSetAlarm = calendarSetTime.getTime().getTime() - calendarCurrent.getTime().getTime();
                    if (timeSetAlarm <= 0) {
                        calendarSetTime.add(Calendar.DATE, 1);
                        Date beingAdd = calendarSetTime.getTime();
                        //timeSetAlarm = beingAdd.getTime() - calendarCurrent.getTime().getTime();
                    }

                    SharedPreferences sharedPreferences = context.getSharedPreferences("metadata", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fromButtonTatDialog", "true");
                    editor.commit();
                    Log.i("AAA", "STATE INDEX : " + state);

                    updateInDatabaseAndCancelOrCreatePendingIntent(state, timeElementNew, calendarSetTime.getTimeInMillis());
                } else {
                    Log.i("AAA", "THE SAME ALARM");
                }

                checkHasShowToast = false;
                isRetrieveFromDatabase = false;
                alarmSettingsDialog.dismiss();
            }
        });
        alarmSettingsDialog.setCanceledOnTouchOutside(true);
        alarmSettingsDialog.getWindow().setGravity(Gravity.CENTER);
        alarmSettingsDialog.setContentView(viewAlarmSettingsDialog);
    }

    //T???O RA M???T B??O TH???C M???I V???I C??C THAM S??? TRUY???N V??O
    public TimeElement createTimeElement(String hour, String minute, String note, String regular, String timeCountdown, boolean state, boolean vibrate) {
        return new TimeElement(hour, minute, note, regular, timeCountdown, state, vibrate);
    }

    //CH???NH S???A L???I C??I SORT N??Y
    public void sortListAlarm(List<TimeElement> list) {
        list.sort(new Comparator<TimeElement>() {
            @Override
            public int compare(TimeElement t1, TimeElement t2) {

                return t1.compareTwoObject(t2);
            }
        });
    }

    public void onBackPressedTrue() {
        isRetrieveFromDatabase = false;
        checkOnBackPressed = true;
        checkShowAllCheckBox = false;
        checkBeingLongClick = false;

        for (int i = 0; i < timeElementList.size(); i++) {
            timeElementList.get(i).setCheckedDelete(false);
        }

        notifyItemRangeChanged(0, timeElementList.size());
    }

    public void setCheckHasShowToastToFalseFromMainActivity() {
        checkHasShowToast = false;
        checkShowAllCheckBox = false;
        checkOnBackPressed = false;
    }

    //X??A C??C ITEMS ???????C CH???N
    public void deleteItemsSelected() throws InterruptedException {
        int size = timeElementList.size();
        //T???O 1 LIST M???I ????? CH???A C??C ITEMS ???????C CH???N
        List<TimeElement> listTemp = new ArrayList<>();

        List<String> stringList = new ArrayList<String>();

        for (int i = 0; i < timeElementList.size(); i++) {
            if (timeElementList.get(i).getCheckedDelete()) {
                listTemp.add(timeElementList.get(i));
                stringList.add(String.valueOf(timeElementList.get(i).getIdAlarm()));
            }
        }

        //X??A B??? DANH S??CH C??C ITEMS ???????C CH???N
        Thread a;
        a = new Thread(new Runnable() {
            @Override
            public void run() {
                saveDataToSQLite.deleteItemsInDatabase(stringList);
            }
        });
        a.start();
        a.join();
        timeElementList.removeAll(listTemp);
        //S???P X???P DANH S??CH B??O TH???C
        sortListAlarm(timeElementList);
        checkOnBackPressed = true;
        checkShowAllCheckBox = false;
        checkBeingLongClick = false;
        notifyItemRangeRemoved(0, size);

        //M??? L???I C??C ICON R???NG KHI KH??NG C?? B??O TH???C N??O HI???N T???I
        mAlarmFragment.hideEmptyAlarmIcon(timeElementList.size() != 0);
    }

    //H??M SELECT T???T C??? C??C ITEMS(B??O TH???C) HI???N C??
    public void selectAllAlarmToDelete() {
        boolean checkAllItems = checkAllItemsIsChecked();
        Log.i("AAA", "STATE DELETE : " + checkAllItems);
        if (checkAllItems) {
            for (TimeElement timeElement : timeElementList) {
                timeElement.setCheckedDelete(false);
            }
        } else {
            for (TimeElement timeElement : timeElementList) {
                timeElement.setCheckedDelete(true);
            }
        }
        notifyItemRangeChanged(0, timeElementList.size());
    }

    public boolean checkAllItemsIsChecked() {
        for (TimeElement timeElement : timeElementList) {
            Log.i("AAA", "BEFORE DELETE : " + timeElement.getCheckedDelete());
            if (!timeElement.getCheckedDelete()) {
                return false;
            }
        }
        return true;
    }

    //KI???M TRA XEM HAI B??O TH???C N??Y C?? PH???I L?? KH??C NHAU V??? STATE C??N C??C TH??NH PH???N KH??C
    //L?? NH?? NHAU N???U ????NG RETURN TRUE ELSE RETURN FALSE
    public boolean checkJustDiffOnOffFunction(TimeElement oldClock, TimeElement newClock) {
        return oldClock.getOnOrOff() != newClock.getOnOrOff() &&
                oldClock.getHour().equals(newClock.getHour()) &&
                oldClock.getMinute().equals(newClock.getMinute()) &&
                oldClock.getNote().equals(newClock.getNote());
    }

    public void callCheckHasShowToastTrueOnScrollOfRecyclerview() {
        checkHasShowToast = true;
        checkShowAllCheckBox = false;
        checkOnBackPressed = false;
        checkBeingLongClick = false;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void retrieveItemsFromDatabase() {
        List<TimeElement> tempList = new ArrayList<>();
        Cursor items = saveDataToSQLite.queryToGetDataReturn("SELECT * FROM " + SaveDataToSQLite.TABLE_NAME);
        if (items.getCount() > 0) {

            while (items.moveToNext()) {
                TimeElement timeElement = new TimeElement();
                timeElement.setIdAlarm(items.getInt(0));
                timeElement.setHour(items.getString(1));
                timeElement.setMinute(items.getString(2));
                timeElement.setNote(items.getString(3));
                timeElement.setRegular(items.getString(4));
                timeElement.setTimeCountdown(items.getString(5));
                timeElement.setStateOnOrOff(items.getInt(6) == 1);
                timeElement.setVibrate(items.getInt(7) == 1);
                tempList.add(timeElement);
            }

            timeElementList = tempList;
            sortListAlarm(timeElementList);
            updateTimeRemainBeforeSetToView();
            mAlarmFragment.hideEmptyAlarmIcon(true);
            isRetrieveFromDatabase = true;
            notifyDataSetChanged();
        } else {
            mAlarmFragment.hideEmptyAlarmIcon(false);
        }
        saveDataToSQLite.close();
    }

    public void updateTimeRemainBeforeSetToView() {
        for (TimeElement item : timeElementList) {
            item.setTimeCountdown(findTimeRemain(Integer.parseInt(item.getHour()), Integer.parseInt(item.getMinute())));
            saveDataToSQLite.queryUpdateTimeRemain(item.getTimeCountdown(), item.getIdAlarm());
        }
    }

    public void callUpdate(TimeElement timeElement) {
        int index = findPosition(timeElement);
        timeElementList.get(index).setStateOnOrOff(false);
        timeElementList.get(index).setTimeCountdown("");
        Log.i("AAA", "POSITION DELETE : " + index);
        //CH??A UPDATE STATE ??? DATABASE V?? C???P NH???T H???Y B??? PENDING INTENT
        isRetrieveFromDatabase = true;
        notifyItemChanged(index);
    }

    public void updateInDatabaseAndCancelOrCreatePendingIntent(int state, TimeElement timeElement, long timeSet) {
        //??? CH??? SET B??O TH???C L???P L???I NHI???U L???N KI???M TRA XEM
        //C?? C??N B??O TH???C ??? NG??Y N??O N???A KH??NG N???U C?? TH?? ADD TH??M NG??Y V?? T???O B??O TH???C (B??O TH???C PH???I B???T T???T TH?? B??? QUA)
        //CH???NH L???I CH??? L???A CH???N KHI T???T B??O TH???C KHI C?? REGULAR H???NG NG??Y
        //STATE OFF (0)
        //C??N OFF TH?? UPDATE L???I UI V?? UPDATE D?????I DATABASE
        //UI TH?? PH???N TIME REMAIN SET NULL V?? DATABASE C??NG V???Y
        //KHI N??O B???T TH?? UPDATE 2 C??I ???? L???I
        if (state == 0) {

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Cursor cursor = new SaveDataToSQLite(context).queryToGetDataReturn("SELECT * FROM " + SaveDataToSQLite.TABLE_NAME_PENDING_INTENT +
                    " WHERE " +
                    SaveDataToSQLite.COLUMN_NAME_REQUEST_CODE + "=" + timeElement.getIdAlarm());

            cursor.moveToFirst();
            String textFromCursor = Arrays.toString(cursor.getBlob(1)).replace("[]", "");
            if (!textFromCursor.isEmpty()) {
                @SuppressLint("Recycle")
                Parcel parcel = Parcel.obtain();
                parcel.unmarshall(cursor.getBlob(1), 0, Arrays.toString(cursor.getBlob(1)).length());
                parcel.setDataPosition(0);
                CustomPendingIntent customPendingIntent = (CustomPendingIntent) parcel.readValue(CustomPendingIntent.class.getClassLoader());
                PendingIntent pendingIntent_ = customPendingIntent.getPendingIntent(context.getApplicationContext());
                pendingIntent_.cancel();
                alarmManager.cancel(pendingIntent_);

                //UPDATE STATE ON OFF AND NULL TO PENDING INTENT COLUMN IN DATABASE
            }
            updateStateOnOffInDatabase(0, timeElement);
        }
        //STATE ON (1)
        //CH???NH L???I STATE N???U L?? 1 TH?? T??NH L???I TIME-REMAIN SET L???I TIME REMAIN
        //UPDATE L???I D?????I DATABASE V?? UPDATE UI
        if (state == 1) {
            //CREATE ALARM
            try {
                createAlarm(timeSet, timeElement);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //UPDATE IN DATABASE(STATE ON)
            updateStateOnOffInDatabase(1, timeElement);
        }
    }

    public String calculateTimeRemain(int hours, int minutes) {
        return hours > 0 ? minutes > 0 ? "B??o th???c sau " + hours + " gi??? " + minutes + " ph??t"
                : "B??o th???c sau " + hours + " gi??? " :
                "B??o th???c sau " + minutes + " ph??t";
    }

    public String calculateTimeRemainToString(String hours, String minutes) {
        Calendar calendarCurrent = Calendar.getInstance();
        Calendar calendarTimeSet = Calendar.getInstance();
        calendarTimeSet.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hours));
        calendarTimeSet.set(Calendar.MINUTE, Integer.parseInt(minutes));

        long timeRemainSub = calendarTimeSet.getTime().getTime() - calendarCurrent.getTime().getTime();

        if (timeRemainSub <= 0) {
            calendarTimeSet.add(Calendar.DATE, 1);
            Date dateNew = calendarTimeSet.getTime();
            timeRemainSub = dateNew.getTime() - calendarCurrent.getTime().getTime();
        }

        int hour = (int) ((timeRemainSub / (1000 * 60 * 60)) % 24);
        int minute = (int) ((timeRemainSub / (1000 * 60)) % 60);

        return calculateTimeRemain(hour, minute);
    }

    public void createAlarm(long timeSet, TimeElement timeElement) throws InterruptedException {

        boolean checkOver60Minutes = timeElement.getTimeCountdown().contains("gi???");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context.getApplicationContext(), AlarmFragment.CustomBroadcast.class);

        Bundle bundleSendToBroadcast = new Bundle();
        bundleSendToBroadcast.putString("state", "on");
        bundleSendToBroadcast.putSerializable("timeEelement", timeElement);

        intent.setAction("runBackground");
        intent.putExtra("bundle", bundleSendToBroadcast);

        //T???O PENDING INTENT V?? T???O B??O TH???C
        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(context, timeElement.getIdAlarm(), intent, PendingIntent.FLAG_CANCEL_CURRENT);
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeSet, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeSet, pendingIntent);
        }

        CustomPendingIntent customPendingIntent = CustomPendingIntent.getBroadcast(timeElement.getIdAlarm(), intent, PendingIntent.FLAG_CANCEL_CURRENT);
        Parcel parcel = Parcel.obtain();
        parcel.writeValue(customPendingIntent);
        byte[] bytesArrayPendingIntent = parcel.marshall();
        parcel.recycle();

        //SAVE PENDING INTENT TO DATABASE
        Thread a;
        a = new Thread(new Runnable() {
            @Override
            public void run() {
                saveDataToSQLite.updateDataToTablePendingIntent(timeElement.getIdAlarm(), bytesArrayPendingIntent);
                saveDataToSQLite.close();
            }
        });
        a.start();
        a.join();
    }

    public void updateStateOnOffInDatabase(int state, TimeElement timeElement) {

        saveDataToSQLite.queryToUpdateDataToDatabase(timeElement);

        if (state == 0) {
            saveDataToSQLite.updateDataToTablePendingIntent(timeElement.getIdAlarm(), new byte[]{});
        }
        saveDataToSQLite.close();

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createNotificationChannelAndNotification(TimeElement timeElement) {
        createNotificationChannel();
        NotificationManagerCompat.from(context).cancelAll();
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(timeElement.getIdAlarm(), createNotification(timeElement));
    }

    public int findPosition(TimeElement timeElement) {
        for (int i = 0; i < timeElementList.size(); i++) {
            if (timeElementList.get(i).getIdAlarm() == timeElement.getIdAlarm()) {
                return i;
            }
        }
        return -1;
    }

    public void runTimeUpdateTimeRemainForSingleItemView() {

        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.i("AAA", "IS RUNNING UPDATE : " + (aCountDown++));
                if (listItemAlarm.isEmpty() || listItemAlarm == null || timeElementList.isEmpty() || timeElementList == null) {
                    return;
                }
                for (int i = 0; i < timeElementList.size(); i++) {
                    Log.i("AAA", "ITEM INDEX : " + timeElementList.get(i).getHour() + ":" + timeElementList.get(i).getMinute() + "--" + timeElementList.get(i).getOnOrOff());
                    int finalI = i;
                    TimeElement timeElementTemp = timeElementList.get(i);
                    if (timeElementTemp.getOnOrOff()) {
                        listItemAlarm.get(i).textViewTimeCountdown.post(new Runnable() {
                            @SuppressLint("SetTextI18n")
                            @Override
                            public void run() {
                                String timeRemain = " | " + findTimeRemain(Integer.parseInt(timeElementTemp.getHour()), Integer.parseInt(timeElementTemp.getMinute()));
                                //SAVE TIME REMAIN CHANGED TO OBJECT AND UPDATE VIEW
                                listItemAlarm.get(finalI).textViewTimeCountdown.setText(timeRemain);
                                timeElementList.get(finalI).setTimeCountdown(timeRemain);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //SAVE TIME REMAIN CHANGED TO DATABASE
                                        saveDataToSQLite.queryUpdateTimeRemain(timeRemain, timeElementTemp.getIdAlarm());
                                    }
                                }).start();
                            }
                        });
                    }

                    Log.i("AAA", "TEXT VIEW GET : " + listItemAlarm.get(i).textViewHour.getText().toString());

                    //JUST CHANGE VIEW NOT SAVE TO DATABASE AND OBJECT
                    //JUST CHANGE VIEW NOT SAVE TO DATABASE AND OBJECT
                    //JUST CHANGE VIEW NOT SAVE TO DATABASE AND OBJECT
                    //JUST CHANGE VIEW NOT SAVE TO DATABASE AND OBJECT
                    //JUST CHANGE VIEW NOT SAVE TO DATABASE AND OBJECT
                    // SCHEDULE ALARM RUN FOLLOW USERS SETTINGS
                }
            }
        };
        timer.schedule(timerTask, 0, 60000);
    }

    public String findTimeRemain(int a, int b) {
        Calendar current = Calendar.getInstance();
        Calendar setTime = Calendar.getInstance();
        setTime.set(Calendar.HOUR_OF_DAY, a);
        setTime.set(Calendar.MINUTE, b);

        int hours = 0, minutes = 0;
        long milliseconds = setTime.getTime().getTime() - current.getTime().getTime();

        if (milliseconds <= 0) {
            setTime.add(Calendar.DATE, 1);
            Date dateNew = setTime.getTime();
            milliseconds = dateNew.getTime() - current.getTime().getTime();
        }
        hours = (int) ((milliseconds / (1000 * 60 * 60)) % 24);
        minutes = (int) ((milliseconds / (1000 * 60)) % 60);
        Log.i("AAA", "TIME REMAIN : " + hours + ":" + minutes);
        return "B??o th???c sau " + (hours <= 0 ? "" : hours + " gi??? ")
                + (minutes <= 0 ? "" : minutes + " ph??t ").replace("\\s{2,}", " ").trim();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewHour, textViewNote, textViewRegular, textViewTimeCountdown;
        SwitchMaterial switchOnOff;
        RelativeLayout itemAlarmLayout;
        CheckBox checkBoxSelectedDelItem;
        TimeElement timeElement;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewHour = itemView.findViewById(R.id.textViewHour);
            textViewNote = itemView.findViewById(R.id.textViewNote);
            textViewRegular = itemView.findViewById(R.id.textViewRegular);
            textViewTimeCountdown = itemView.findViewById(R.id.textViewTimeCountdown);
            switchOnOff = itemView.findViewById(R.id.switchOnOff);
            itemAlarmLayout = itemView.findViewById(R.id.alarmItemLayout);
            checkBoxSelectedDelItem = itemView.findViewById(R.id.checkBoxSelectedDelItem);
        }


        public void setTimeElement(TimeElement a) {
            this.timeElement = a;
        }
    }

}
