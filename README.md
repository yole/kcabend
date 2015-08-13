This is a sample demonstrating the use of Kotlin for developing the back-end of a real-life Web application.
The core logic of the application is independent of any particular framework; the current implementation uses
[Ktor](https://github.com/JetBrains/ktor) as the Web framework and raw JDBC for persistence.

The fact that the sample implements much of the functionality of a somewhat well-known Web service which has recently
been shut down may or not be an accident. :)

== Compiling and Running

The application requires Kotlin plugin 0.13.511, which is newer than the latest official release, M12. To install the
plugin:

 * Install the latest EAP build of IntelliJ IDEA Community Edition version 15
 * Go to Settings | Plugins | Browse Repositories | Manage Repositories
 * Add [this repository](https://teamcity.jetbrains.com/repository/download/bt345/.lastSuccessful/updatePlugins.xml) to the list
 * Select Kotlin in the list from the list, press the "Update" button.

To build the application, open the checked out directory as an IntelliJ IDEA project. Use the provided run configurations
to run the test and the application itself.

The application uses PostgreSQL for persistence. To set up the database:

 * Install PostgreSQL normally
 * Create a database
 * Create the tables using the script resources/schema.sql
 * Edit resources/application.conf and ensure the database connection string (server, port, database name, user and password) is correct.

The frontend for the application is developed in the [Pepyatka project](https://github.com/pepyatka/pepyatka-html).
Please follow the instructions in the linked repository to configure and run the frontend.
