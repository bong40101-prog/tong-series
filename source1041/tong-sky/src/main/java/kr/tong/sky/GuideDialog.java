package kr.tong.sky;
import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Offline guide; opening it never writes app data or settings. */
public final class GuideDialog extends DialogFragment {
    private ScrollView scroll;
    private int position;
    public static void show(Activity activity) {
        if (!activity.isFinishing() && !activity.isDestroyed()
                && activity.getFragmentManager().findFragmentByTag("usage-guide") == null)
            new GuideDialog().show(activity.getFragmentManager(), "usage-guide");
    }
    private int dp(int n) {return Math.round(n * getResources().getDisplayMetrics().density);}
    @Override public Dialog onCreateDialog(Bundle saved) {
        Activity a=getActivity();position=saved==null?0:saved.getInt("scroll",0);
        scroll=new ScrollView(a);scroll.setContentDescription("사용방법 본문");
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24),dp(4),dp(24),dp(20));scroll.addView(body);
        String[][] sections=new String[][] {{"저장된 일정 보기","앱을 열면 GitHub에 공개 저장된 대한항공·아시아나 자료를 불러옵니다. 항공사와 지역·도시를 선택하고 가는 편·오는 편의 달력을 확인하세요.\n\n연결이 안 될 때는 ‘다시 불러오기’ 또는 ‘기존 자료로 보기’를 선택할 수 있습니다. 이전 자료를 볼 때는 수집 시각을 함께 확인하세요."},{"새 자료 수집 설정","저장된 결과를 보는 데는 GitHub 연결이 필요하지 않습니다. 새 자료를 수집해 공유하려면 설정 메뉴의 ‘GitHub 저장 연결’을 먼저 완료하세요.\n\n‘전체 노선 검색’ 또는 지역의 ‘양방향 조회’를 누르면 조회를 시작합니다. 한국 시간 오늘 성공한 항목은 재사용하고 필요한 항목을 이어서 수집합니다. 날짜가 바뀌어도 기존 저장 자료를 삭제하지 않습니다."},{"백그라운드 수집","다른 앱을 사용하거나 화면을 꺼도 수집을 이어가며 알림에서 진행 상태와 중단 버튼을 확인할 수 있습니다. 알림 권한과 휴대폰의 배터리·백그라운드 실행 설정을 확인하세요.\n\n앱 강제 종료, 휴대폰 재부팅, 시스템 실행 제한이 있으면 중단될 수 있습니다. 다시 검색하면 완료한 자료를 재사용합니다. 계속 실패하면 설정 메뉴의 ‘수집 오류 기록’을 확인하세요."},{"관심 검색과 추천일정","상단 ‘＋ 관심 검색’에서 관심 조건을 추가하세요. 추천일정에서는 유럽·미주 지역과 여행 일수를 고르고, IN 도착 도시와 OUT 출발 도시를 선택할 수 있습니다. 도시를 선택하지 않은 쪽은 해당 지역 전체에서 찾습니다.\n\n보너스 왕복, 보너스＋승급, 승급＋보너스의 세 조합을 저장된 자료로 보여줍니다. 같은 도시와 서로 다른 도시의 입출국을 모두 찾을 수 있습니다."},{"달력 숫자의 의미","숫자는 좌석 개수가 아니라 해당 표식이 있는 날짜 수입니다. 프레스티지 보너스·프레스티지 승급·퍼스트 보너스/승급 표식을 구분해 보여줍니다. 퍼스트는 원본이 합산 표식입니다.\n\n미확인·조회 실패는 ‘좌석 없음’과 다릅니다. 저장 이후 상황이 달라질 수 있으므로 예약 전 항공사에서 실제 가능 여부를 확인하세요."},{"업데이트와 자료 보존","기존 앱을 삭제하지 말고 새 APK를 그 위에 설치하세요. 설명서를 열거나 업데이트하는 동작은 저장 자료를 초기화하지 않습니다. 앱 삭제·데이터 삭제는 기기 내 관심 조건과 설정을 지웁니다."}};
        for(String[] section:sections){
            TextView heading=new TextView(a);heading.setText(section[0]);heading.setTextSize(18);
            heading.setTypeface(null,android.graphics.Typeface.BOLD);heading.setPadding(0,dp(18),0,dp(6));
            if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);body.addView(heading);
            TextView text=new TextView(a);text.setText(section[1]);text.setTextSize(15);
            text.setLineSpacing(dp(4),1f);text.setTextIsSelectable(true);body.addView(text);
        }
        return new AlertDialog.Builder(a).setTitle("통SKY · 사용방법").setView(scroll)
            .setPositiveButton("닫기",(dialog,which)->dismiss()).create();
    }
    @Override public void onStart(){super.onStart();Window w=getDialog().getWindow();
        if(w!=null)w.setLayout(Math.min(dp(680),getResources().getDisplayMetrics().widthPixels-dp(32)),WindowManager.LayoutParams.WRAP_CONTENT);
        scroll.post(()->scroll.scrollTo(0,position));
    }
    @Override public void onSaveInstanceState(Bundle state){
        state.putInt("scroll",scroll==null?position:scroll.getScrollY());super.onSaveInstanceState(state);
    }
}
