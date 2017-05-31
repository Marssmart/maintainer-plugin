# maintainer-plugin
Complex reviewer management for gerrit

Basic plugin features

- add reviewers based on component matching by maintainers file
- add +2 review if all components has been +1 by respecive maintainers
- auto submit
- add warnings if file review changes component of affected file
- complex info about which file falls under which component and
   which files has not been matched
   
Configuration
 Uses standard gerrit plugin configuration like so[maintainer.config]

 [branch "refs/heads/*"]
 - pluginuser = maintainer-plugin - user on whos behalf plugin actions will be done
 - maintainerfileref = master/HEAD - reference for maintainer file that should be used
 - maintainerfile = MAINTAINER - absolute path within repo where maintainer file is stored
 - autoaddreviewers = true - if true, automaticaly matchses pachset files under their component based of maintainers file configuration
 - allowmaintainersubmit = true - if true, automaticaly post +2 on patch after all respective component maintainers have added +1
 - autosubmit = true - if true, after previous step automaticaly submits patch
   
