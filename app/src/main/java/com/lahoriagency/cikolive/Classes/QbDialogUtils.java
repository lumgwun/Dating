package com.lahoriagency.cikolive.Classes;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.quickblox.chat.model.QBChatDialog;
import com.quickblox.chat.model.QBDialogType;
import com.quickblox.chat.utils.DialogUtils;
import com.quickblox.users.model.QBUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class QbDialogUtils {
    private static final String TAG = QbDialogUtils.class.getSimpleName();
    private static final Gson gson = new Gson();


    public static QBChatDialog createDialog(List<QBUser> users, String chatName) {
        if (isPrivateChat(users)) {
            QBUser currentUser = ChatHelper.getCurrentUser();
            users.remove(currentUser);
        }
        QBChatDialog dialog = DialogUtils.buildDialog(users.toArray(new QBUser[users.size()]));
        if (!TextUtils.isEmpty(chatName)) {
            dialog.setName(chatName);
        }
        return dialog;
    }

    private static boolean isPrivateChat(List<QBUser> users) {
        return users.size() == 2;
    }
    public static List<QBUser> getAddedUsers(List<QBUser> previousUsers, List<QBUser> currentUsers) {
        List<QBUser> addedUsers = new ArrayList<>();
        for (QBUser currentUser : currentUsers) {
            boolean wasInChatBefore = false;
            for (QBUser previousUser : previousUsers) {
                if (currentUser.getId().equals(previousUser.getId())) {
                    wasInChatBefore = true;
                    break;
                }
            }
            if (!wasInChatBefore) {
                addedUsers.add(currentUser);
            }
        }

        QBUser currentUser = ChatHelper.getCurrentUser();
        addedUsers.remove(currentUser);

        return addedUsers;
    }
    public static List<QBUser> getRemovedUsers(List<QBUser> previousUsers, List<QBUser> currentUsers) {
        List<QBUser> removedUsers = new ArrayList<>();
        for (QBUser previousUser : previousUsers) {
            boolean isUserStillPresented = false;
            for (QBUser currentUser : currentUsers) {
                if (previousUser.getId().equals(currentUser.getId())) {
                    isUserStillPresented = true;
                    break;
                }
            }
            if (!isUserStillPresented) {
                removedUsers.add(previousUser);
            }
        }

        QBUser currentUser = ChatHelper.getCurrentUser();
        removedUsers.remove(currentUser);

        return removedUsers;
    }


    public static void logDialogUsers(QBChatDialog qbDialog) {
        Log.v(TAG, "Dialog " + getDialogName(qbDialog));
        logUsersByIds(qbDialog.getOccupants());
    }

    public static void logUsers(List<QBUser> users) {
        for (QBUser user : users) {
            Log.i(TAG, user.getId() + " " + user.getFullName());
        }
    }

    private static void logUsersByIds(List<Integer> users) {
        for (Integer id : users) {
            QBUser user = QbUsersHolder.getInstance().getUserById(id);
            Log.i(TAG, ((user != null && user.getId() != null) ? (user.getId() + " " + user.getLogin()) : "noId"));
        }
    }

    public static String getDialogName(QBChatDialog dialog) {
        if (dialog.getType().equals(QBDialogType.GROUP)) {
            return dialog.getName();
        } else {
            // It's a private dialog, let's use opponent's name as chat name
            Integer opponentId = dialog.getRecipientId();
            QBUser user = QbUsersHolder.getInstance().getUserById(opponentId);
            if (user != null) {
                return TextUtils.isEmpty(user.getFullName()) ? user.getLogin() : user.getFullName();
            } else {
                return dialog.getName();
            }
        }
    }



    public static List<Integer> getOccupantsIdsListFromString(String occupantIds) {
        List<Integer> occupantIdsList = new ArrayList<>();
        if (occupantIds != null) {
            String[] occupantIdsArray = occupantIds.split(",");
            for (String occupantId : occupantIdsArray) {
                occupantIdsList.add(Integer.valueOf(occupantId.trim()));
            }
        }
        return occupantIdsList;
    }

    public static String getOccupantsIdsStringFromList(Collection<Integer> occupantIdsList) {
        return TextUtils.join(",", occupantIdsList);
    }

    public static String getOccupantsNamesStringFromList(Collection<QBUser> qbUsers) {
        ArrayList<String> userNameList = new ArrayList<>();
        for (QBUser user : qbUsers) {
            if (TextUtils.isEmpty(user.getFullName())) {
                userNameList.add(user.getLogin());
            } else {
                userNameList.add(user.getFullName());
            }
        }
        return TextUtils.join(", ", userNameList);
    }

    public static QBChatDialog buildPrivateChatDialog(String dialogId, Integer recipientId) {
        QBChatDialog chatDialog = DialogUtils.buildPrivateDialog(recipientId);
        chatDialog.setDialogId(dialogId);

        return chatDialog;
    }

    public static QBChatDialog createDialog(List<QBUser> users) {
        QBUser currentUser = ChatHelper.getCurrentUser();
        users.remove(currentUser);

        return DialogUtils.buildDialog(users.toArray(new QBUser[users.size()]));
    }

    public static List<QBUser> getAddedUsers(QBChatDialog dialog, List<QBUser> currentUsers) {
        return getAddedUsers(getQbUsersFromQbDialog(dialog), currentUsers);
    }

    public static List<QBUser> getRemovedUsers(QBChatDialog dialog, List<QBUser> currentUsers) {
        return getRemovedUsers(getQbUsersFromQbDialog(dialog), currentUsers);
    }


    public static Integer[] getUserIds(List<QBUser> users) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (QBUser user : users) {
            ids.add(user.getId());
        }
        return ids.toArray(new Integer[ids.size()]);
    }



    private static List<QBUser> getQbUsersFromQbDialog(QBChatDialog dialog) {
        List<QBUser> previousDialogUsers = new ArrayList<>();
        for (Integer id : dialog.getOccupants()) {
            QBUser user = QbUsersHolder.getInstance().getUserById(id);
            if (user == null) {
                throw new RuntimeException("User from dialog is not in memory. This should never happen, or we are screwed");
            }
            previousDialogUsers.add(user);
        }
        return previousDialogUsers;
    }

    public static List<String> getQBUserPhotos(QBChatDialog dialog) {
        List<String> userPhotos = new ArrayList<>();
        Integer loggedUserId = SharedPrefsHelper.getInstance().getQbUser().getId();
        for (Integer i : dialog.getOccupants()) {
            if (!i.equals(loggedUserId)) {
                QBUser user = QbUsersHolder.getInstance().getUserById(i);
                if (user != null)
                    userPhotos.add(gson.fromJson(user.getCustomData(), QBUserCustomData.class).getProfilePhotoData().get(0).getLink());
            }
        }
        return userPhotos;
    }



}
