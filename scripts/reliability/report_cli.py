"""Ubuntu 내부 전용 DB에 관리자 재개 명령을 전달한다. shell 문자열을 만들지 않는다."""
import os,subprocess,sys,uuid
import local_runtime as runtime

def main():
    if os.name!='posix':raise SystemExit('Ubuntu 안에서 실행하세요')
    args=sys.argv[1:]
    if len(args)!=4:raise SystemExit('requestId commandId actorUserId reason 인수가 필요합니다')
    args[0]=str(uuid.UUID(args[0]));args[1]=str(uuid.UUID(args[1]))
    if not args[2].isascii() or not args[2].isdecimal() or int(args[2])<1:raise SystemExit('관리자 ID를 확인하세요')
    if not args[3].strip() or len(args[3])>1000:raise SystemExit('사유는 1~1000자로 입력하세요')
    if runtime.owned_process() is not None:raise SystemExit('실행 중인 앱의 클래스 파일 보호를 위해 먼저 local stop-app을 수행하세요')
    runtime.sync_workspace();runtime.setup()
    subprocess.run(['bash','./gradlew','prepareCustomReportCli','--console=plain','--no-daemon'],cwd=runtime.WORKSPACE,env=runtime.environment(),check=True)
    classpath=(runtime.WORKSPACE/'build/portfolio-report-classpath.txt').read_text().strip()
    os.chdir(runtime.WORKSPACE)
    os.execvpe('java',['java','-Xmx128m','-cp',classpath,'com.aivle.project.reporting.internal.company.reportjob.cli.ReportRecoveryCommand']+args,runtime.environment())

if __name__=='__main__':main()
