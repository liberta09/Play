package com.lagradost.cloudstream3.utils

import android.content.Context

const val VIDEO_POS_DUR = "video_pos_dur"
const val VIDEO_WATCH_STATE = "video_watch_state"
const val RESULT_WATCH_STATE = "result_watch_state"
const val RESULT_WATCH_STATE_DATA = "result_watch_state_data"
const val RESULT_SUBSCRIBED_STATE_DATA = "result_subscribed_state_data"
const val RESULT_FAVORITES_STATE_DATA = "result_favorites_state_data"
const val RESULT_RESUME_WATCHING = "result_resume_watching_2"
const val RESULT_RESUME_WATCHING_OLD = "result_resume_watching"

fun <T> setKey(path: String, value: T) = DataStore.setKey(path, value)
fun <T> setKey(folder: String, path: String, value: T) = DataStore.setKey(folder, path, value)
fun <T> getKey(path: String, defVal: T? = null): T? = DataStore.getKey(path, defVal)
fun <T> getKey(folder: String, path: String, defVal: T? = null): T? = DataStore.getKey(folder, path, defVal)
fun <T : Any> getKey(path: String, valueType: Class<T>): T? = DataStore.getKey(path, valueType)
fun removeKey(path: String) = DataStore.removeKey(path)
fun removeKey(folder: String, path: String) = DataStore.removeKey(folder, path)
fun containsKey(path: String): Boolean = DataStore.containsKey(path)
fun containsKey(folder: String, path: String): Boolean = DataStore.containsKey(folder, path)
