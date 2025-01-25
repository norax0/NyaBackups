# NyaBackup

> [!WARNING]
> there are definitely some issues with the plugin
> you can use it,it works, but it's a-bit barebones right now.

## Why?
because most backup plugins are insanely hard to use,and "bloated", instead NyaBackups tries to achieve the smallest possible backup size,for quantity over speed.
This means backups will take less storage space,but will take a bit longer to load,which if you are not constantly loading backups back and forth then it should not really be an issue.

## Where download?
you can download them in the releases section,any release with the "pre-release" tag is automatically built upon a commit.

## Compiling
> [!NOTE]
> those instructions are meant for linux based systems and not tested on windows!

```
git clone https://github.com/norax0/NyaBackup
cd NyaBackup
mvn package
```
the release jar will be in target/NyaBackup-{version}

## Roadmap
- [X] Load/create backups.
- [ ] Cache files.
- [ ] Achieve 0 (total) code warnings.
- [X] Automatic scheduled backups.
- [X] Resource monitoring.
- [ ] Upload to online services like Google drive.
- [ ] Do some renaming.
- [ ] Proper tests.
- [ ] Workflows (release/build/test)
- [X] Tab completitions.

## Drafts
- [ ] Web ui.
- [ ] Self FTP server.
