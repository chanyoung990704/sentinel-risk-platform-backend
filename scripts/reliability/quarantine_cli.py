"""격리 재전달은 기존 요청의 고정 입력을 사용한다. 인수를 shell 문자열로 만들지 않는다."""
import os,subprocess,sys,uuid
import local_runtime as runtime

def main():
    if os.name!='posix':raise SystemExit('Ubuntu 안에서 실행하세요')
    args=sys.argv[1:]
    if len(args)!=5:raise SystemExit('quarantineId commandId actorUserId targetRequestId reason 인수가 필요합니다')
    for index in (0,1,3):args[index]=str(uuid.UUID(args[index]))
    if not args[2].isascii() or not args[2].isdecimal() or int(args[2])<1:raise SystemExit('관리자 ID를 확인하세요')
    if not args[4].strip() or len(args[4])>1000:raise SystemExit('사유는 1~1000자로 입력하세요')
    if runtime.owned_process() is not None:raise SystemExit('먼저 local stop-app을 수행하세요')
    runtime.sync_workspace();runtime.setup()
    subprocess.run(['bash','./gradlew','prepareCustomReportCli','--console=plain','--no-daemon'],cwd=runtime.WORKSPACE,env=runtime.environment(),check=True)
    classpath=(runtime.WORKSPACE/'build/portfolio-report-classpath.txt').read_text().strip();os.chdir(runtime.WORKSPACE)
    os.execvpe('java',['java','-Xmx128m','-cp',classpath,'com.aivle.project.messaging.quarantine.QuarantineReplayCommand']+args,runtime.environment())

if __name__=='__main__':main()
