# Pleos Connect SDK: AIDL/Parcelable·리플렉션으로 서비스와 데이터를 주고받으므로 통째로 보존
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# AAOS Car API는 플랫폼 제공(useLibrary) — 앱에 포함되지 않음
-dontwarn android.car.**

# 앱 엔티티(Room)·Enum 이름은 서버 동기화 값으로 쓰인다
-keep class com.hidany.odolog.data.** { *; }
-keepclassmembers enum * { *; }
