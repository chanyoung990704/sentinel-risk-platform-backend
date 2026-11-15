"""명시한 요청 1~1000개의 DB·파일 상태를 대조한다. 기존 요청/파일은 변경하지 않는다."""
import os,subprocess,sys,uuid
import local_runtime as runtime

def main():
    if os.name!='posix':raise SystemExit('Ubuntu 안에서 실행하세요')
    if not 1<=len(sys.argv)-1<=1000:raise SystemExit('요청 ID를 1~1000개 지정하세요')
    ids=[str(uuid.UUID(value)) for value in sys.argv[1:]]
    if len(set(ids))!=len(ids):raise SystemExit('중복 요청 ID를 제거하세요')
    if runtime.owned_process() is not None:raise SystemExit('클래스 보호를 위해 먼저 local stop-app을 수행하세요')
    runtime.sync_workspace();runtime.setup()
    subprocess.run(['bash','./gradlew','prepareCustomReportCli','--console=plain','--no-daemon'],cwd=runtime.WORKSPACE,env=runtime.environment(),check=True)
    classpath=(runtime.WORKSPACE/'build/portfolio-report-classpath.txt').read_text().strip();os.chdir(runtime.WORKSPACE)
    os.execvpe('java',['java','-Xmx128m','-cp',classpath,'com.aivle.project.reporting.internal.company.reportjob.cli.ReportReconciliationCommand']+ids,runtime.environment())

if __name__=='__main__':main()
