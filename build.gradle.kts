val isOnCI = System.getenv()["GITHUB_ACTIONS"] != null

allprojects {
    group = "dev.hsbrysk"
    version = "0.0.1" + if (isOnCI) "" else "-SNAPSHOT"
}
