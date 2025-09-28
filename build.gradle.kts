// Top-level build file where you can add configuration options common to all sub-projects/modules.

tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}