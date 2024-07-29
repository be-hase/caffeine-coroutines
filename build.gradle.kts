val isOnCI = System.getenv()["GITHUB_ACTIONS"] != null

allprojects {
    group = "dev.hsbrysk"
    version = "1.2.0" + if (isOnCI) "" else "-SNAPSHOT"
}
