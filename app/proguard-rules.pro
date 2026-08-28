# R8 rules for the release build.
#
# Everything here exists because something in this app would otherwise break in a way the tests
# cannot see: unit tests run against unminified code, so a rule that is missing shows up only on a
# device, and usually as data that will not load rather than as a crash.

# --- Enum constant names are a storage format ------------------------------------------------
#
# Difficulty, GameType, ThemeMode and AppLanguage are written to Room and DataStore by `name`, and
# read back by matching that string (see Difficulty.fromStorageName and friends). Storing the name
# rather than the ordinal is deliberate — an ordinal silently means something else the day an entry
# is inserted in the middle — but it makes the constant's name part of the on-disk format.
#
# R8 is free to rename enum fields, and `name()` returns whatever the field ended up called. Without
# this, a release build would write "a" where a debug build wrote "EASY", and every saved game,
# history row and setting written by an earlier build would fail to parse — quietly, because the
# code treats an unknown name as "not there".
-keepclassmembers enum com.przunk.kratkoza.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Readable stack traces from Play ----------------------------------------------------------
#
# Without these a crash report from a real phone is a list of numbered methods in class `a`. The
# mapping file at app/build/outputs/mapping/release/mapping.txt turns them back into names, and Play
# will do that automatically once it is uploaded alongside the bundle.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
