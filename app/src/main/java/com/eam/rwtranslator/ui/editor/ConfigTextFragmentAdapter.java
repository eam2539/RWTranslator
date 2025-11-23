package com.eam.rwtranslator.ui.editor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eam.rwtranslator.data.model.SectionModel;
import com.eam.rwtranslator.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.HashMap;
import java.util.Map;

public class ConfigTextFragmentAdapter extends RecyclerView.Adapter<ConfigTextFragmentAdapter.ViewHolder>
    implements ConfigTextFragment.ModificationStateCallBack {

  private SectionModel.Pair translationPair;
  private InputMethodManager imm;
  private Context context;
  private sectionEditorActCallBack callback;
  private boolean FirstIsRequest = false;
  private EditText[] edittexts;
  private Map<EditText, Integer> edit2index;

  public ConfigTextFragmentAdapter(
      SectionModel.Pair translationPair,
      Context context,
      sectionEditorActCallBack callback) {
    if (translationPair.getLang_pairs() != null)
      edittexts = new EditText[translationPair.getLang_pairs().size() + 1];
    else edittexts = new EditText[1];
    edit2index = new HashMap<>();
    this.translationPair = translationPair;
    this.context = context;
    this.callback = callback;
    this.imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ViewHolder viewholder =
        new ViewHolder(
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.translater_entry, parent, false));
    return viewholder;
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    if (edittexts[position] == null) {
      edittexts[position] = holder.editText;
      edit2index.put(holder.editText, position);
    }
    if (position == 0) {
      holder.bindFirstItem();
    } else {
      holder.bindItem(position - 1);
    }
  }

  @Override
  public int getItemCount() {
    if (translationPair.getLang_pairs() != null) {
      return translationPair.getLang_pairs().size() + 1;
    } else {
      return 1;
    }
  }

  public class ViewHolder extends RecyclerView.ViewHolder {
    private TextInputEditText editText;
    private TextInputLayout layout;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);
      editText = itemView.findViewById(R.id.translater_dialig_inputEditText);
      layout = itemView.findViewById(R.id.translater_dialig_inputEditLayout);

      editText.addTextChangedListener(
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
              if (s.toString().isEmpty()) {
                layout.setError(context.getResources().getString(R.string.project_act_input_empty_error));
              } else {
                layout.setError(null);
              }
            }
          });
    }

    public void bindFirstItem() {
      String key = translationPair.getKey().getKeyName();
      String val = translationPair.getOri_val();
      new Handler(Looper.getMainLooper())
          .postDelayed(
              () -> {
                if(edittexts.length==1){
                    editText.requestFocus();
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }    
                /*Rect rect = new Rect();
                itemView.getWindowVisibleDisplayFrame(rect);
                if (itemView.getRootView().getHeight() - (rect.bottom - rect.top) > getDip(100.0f)) {

                }*/
              },
              250);
      layout.setHint(key);
      editText.setText(val);
      editText.setSelection(editText.getText().length());
      callback.setPairEntry(layout, editText, edit2index.get(editText), key);
    }

    public void bindItem(int position) {
      Map.Entry<String, String> entry = translationPair.getLangEntryByIndex(position);
      String key = translationPair.getKey().getKeyName() + '_' + entry.getKey();
      String val = entry.getValue();

      layout.setHint(key);
      editText.setText(val);

      callback.setPairEntry(layout, editText, edit2index.get(editText), key);
    }
  }

  public int getDip(float pixels) {
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    float dp = pixels / (metrics.densityDpi / 160f);
    return (int) dp;
  }

  @Override
  public boolean[] getModificationState() {
    boolean[] result = {false, false};
    int i = 1;
    if (translationPair.getLang_pairs() != null) {
      for (Map.Entry<String, String> entry : translationPair.getLang_pairs().entrySet()) {
        String val = edittexts[i].getText().toString();
        if (!entry.getValue().toString().equals(val)) {
          translationPair.getLang_pairs().put(entry.getKey(), val);
          // edittexts[i].setText(val);
          result[0] = true;
        }
        i++;
      }
    }
    String ori_edit_txt = edittexts[0].getText().toString();
    String val = translationPair.getOri_val();
    if (!ori_edit_txt.equals(val)) {
      translationPair.setOri_val(ori_edit_txt);
      result[1] = true;
    }

    return result;
  }

  public void updateEditText(ActivityResult result) {
    if (result.getResultCode() == Activity.RESULT_OK) {
      Intent i = result.getData();
      int index = i.getIntExtra("index", 1);
      edittexts[index].setText(i.getStringExtra("tranEditText"));
    }
  }

  public interface sectionEditorActCallBack {
    void setPairEntry(
        TextInputLayout layout, TextInputEditText valueEditText, Integer index, String key);
  }
}
