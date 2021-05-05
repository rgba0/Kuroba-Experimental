pub mod mapper {
  use new_post_parser_lib::{PostCommentParsed, Spannable, SpannableData, PostLink};
  use jni::sys::{jobject, jsize, _jobject};
  use jni::{JNIEnv, errors};
  use crate::helpers::{format_post_parsing_object_signature, format_spannables_object_signature_pref, format_spannables_object_signature};
  use jni::objects::{JObject, JString, JValue};

  pub fn to_java_object(env: &JNIEnv, post_comment_parsed: &PostCommentParsed) -> errors::Result<jobject> {
    let post_comment_parsed_jclass = env.find_class(format_post_parsing_object_signature("PostCommentParsed").as_str())
      .expect("Failed to find class PostCommentParsed");
    let post_comment_parsed_jobject = env.new_object(post_comment_parsed_jclass, "()V", &[])
      .expect("Failed to instantiate PostCommentParsed");

    env.set_field(
      post_comment_parsed_jobject,
      "commentTextRaw",
      "Ljava/lang/String;",
      JValue::Object(JObject::from(env.new_string(&post_comment_parsed.original_comment_text)?.into_inner()))
    ).expect("Failed to set field commentTextRaw of post_comment_parsed_jobject");

    env.set_field(
      post_comment_parsed_jobject,
      "commentTextParsed",
      "Ljava/lang/String;",
      JValue::Object(JObject::from(env.new_string(&*post_comment_parsed.parsed_comment_text)?.into_inner()))
    ).expect("Failed to set field commentTextParsed of post_comment_parsed_jobject");

    env.set_field(
      post_comment_parsed_jobject,
      "spannableList",
      format_spannables_object_signature_pref("[", "PostCommentSpannable").as_str(),
      JValue::Object(JObject::from(spannables_to_java_object(env, &*post_comment_parsed.spannables)?))
    ).expect("Failed to set field commentTextParsed of post_comment_parsed_jobject");

    return Result::Ok(post_comment_parsed_jobject.into_inner());
  }

  fn spannables_to_java_object(env: &JNIEnv, spannables: &Vec<Spannable>) -> errors::Result<jobject> {
    let post_comment_spannable_data_array_jclass = env.find_class(
      format_spannables_object_signature("IPostCommentSpannableData").as_str()
    ).expect("Failed to find class PostCommentParsed");

    let spannable_array = env.new_object_array(
      spannables.len() as jsize,
      post_comment_spannable_data_array_jclass,
      JObject::null()
    ).expect("Failed to allocate array for spannables");

    for (index, spannable) in spannables.iter().enumerate() {
      match &spannable.spannable_data {
        SpannableData::Link(post_link) => {
          match post_link {
            PostLink::Quote { post_no } => {
              let post_no_param = JValue::Long(*post_no as i64);
              let params = [post_no_param];

              convert_spannable_and_add_to_array(env, spannable_array, index, "Quote", "(J)V", &params);
            }
            PostLink::Dead { post_no } => {
              let post_no_param = JValue::Long(*post_no as i64);
              let params = [post_no_param];

              convert_spannable_and_add_to_array(env, spannable_array, index, "DeadQuote", "(J)V", &params);
            }
            PostLink::UrlLink { link } => {
              let link_param = JValue::Object(JObject::from(env.new_string(link)?.into_inner()));
              let params = [link_param];

              convert_spannable_and_add_to_array(env, spannable_array, index, "UrlLink", "(Ljava/lang/String;)V", &params);
            }
            PostLink::BoardLink { board_code } => {
              let board_code_param = JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()));
              let params = [board_code_param];

              convert_spannable_and_add_to_array(env, spannable_array, index, "BoardLink", "(Ljava/lang/String;)V", &params);
            }
            PostLink::SearchLink { board_code, search_query } => {
              let board_code_param = JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()));
              let search_query_param = JValue::Object(JObject::from(env.new_string(search_query)?.into_inner()));
              let params = [board_code_param, search_query_param];

              convert_spannable_and_add_to_array(env, spannable_array, index, "SearchLink", "(Ljava/lang/String;Ljava/lang/String;)V", &params);
            }
            PostLink::ThreadLink { board_code, thread_no, post_no } => {
              let board_code_param = JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()));
              let thread_no_param = JValue::Long(*thread_no as i64);
              let post_no_param = JValue::Long(*post_no as i64);
              let params = [board_code_param, thread_no_param, post_no_param];

              convert_spannable_and_add_to_array(env, spannable_array, index, "ThreadLink", "(Ljava/lang/String;JJ)V", &params);
            }
          }
        }
        SpannableData::Spoiler => {
          convert_spannable_and_add_to_array(env, spannable_array, index, "Spoiler", "()V", &[]);
        }
        SpannableData::GreenText => {
          convert_spannable_and_add_to_array(env, spannable_array, index, "GreenText", "()V", &[]);
        }
      }
    }

    return Result::Ok(spannable_array);
  }

  fn convert_spannable_and_add_to_array(
    env: &JNIEnv,
    post_comment_spannable_data_array: *mut _jobject,
    index: usize,
    spannable_class_signature: &str,
    ctor_sign: &str,
    ctor_args: &[JValue]
  ) {
    let full_signature = format!("IPostCommentSpannableData${}", spannable_class_signature);

    let spoiler_span_jclass = env.find_class(
      format_spannables_object_signature(full_signature.as_str()).as_str()
    ).expect(format!("Failed to find class {}", full_signature.as_str()).as_str());

    let spannable_object = env.new_object(spoiler_span_jclass, ctor_sign, ctor_args)
      .expect(format!("Failed to instantiate {}", full_signature.as_str()).as_str());

    env.set_object_array_element(
      post_comment_spannable_data_array,
      index as jsize,
      spannable_object
    );
  }
}